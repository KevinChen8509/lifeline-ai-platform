"""测试幻灯片渲染模块"""

from pathlib import Path
from unittest.mock import patch, MagicMock

import pytest

from src.parsers.slide_renderer import render_slides, _find_libreoffice


def test_render_file_not_found(tmp_path):
    """文件不存在时报错"""
    with pytest.raises(FileNotFoundError, match="PPT 文件不存在"):
        render_slides(tmp_path / "nonexistent.pptx", tmp_path / "out")


def test_render_creates_output_dir(tmp_path):
    """自动创建输出目录"""
    ppt = tmp_path / "test.pptx"
    ppt.write_bytes(b"PK")
    out = tmp_path / "nested" / "slides"
    try:
        render_slides(ppt, out)
    except Exception:
        pass
    assert out.exists()


def test_find_libreoffice_none():
    """未安装 LibreOffice 时返回 None"""
    with patch("shutil.which", return_value=None):
        with patch("os.path.isfile", return_value=False):
            assert _find_libreoffice() is None


def test_find_libreoffice_path():
    """能找到 LibreOffice"""
    with patch("shutil.which", return_value="/usr/bin/libreoffice"):
        with patch("os.path.isfile", return_value=True):
            assert _find_libreoffice() == "/usr/bin/libreoffice"


def test_render_no_ppt_no_lo(tmp_path):
    """PowerPoint 和 LibreOffice 都不可用"""
    ppt = tmp_path / "test.pptx"
    ppt.write_bytes(b"PK")

    # Mock PowerPoint COM 抛异常 → 走 LO fallback → LO 也不可用
    mock_app = MagicMock()
    mock_app.Presentations.Open.side_effect = Exception("PowerPoint.Application failed")

    with patch("win32com.client.Dispatch", return_value=mock_app):
        with patch("src.parsers.slide_renderer._find_libreoffice", return_value=None):
            with pytest.raises(RuntimeError, match="LibreOffice 未安装"):
                render_slides(ppt, tmp_path / "out2")


def test_render_ppt_com_success(tmp_path):
    """PowerPoint COM 成功导出"""
    ppt = tmp_path / "test.pptx"
    ppt.write_bytes(b"PK")
    out_dir = tmp_path / "slides"

    # 模拟幻灯片导出 — 创建实际文件
    def make_fake_export(out_dir):
        def fake_export(path, fmt, w, h):
            Path(path).write_bytes(b"\x89PNG")
        return fake_export

    mock_slide = MagicMock()
    mock_slide.Export = make_fake_export(out_dir)

    # presentation.Slides.Count = 2, presentation.Slides(i) → mock_slide
    mock_slides = MagicMock()
    mock_slides.Count = 2
    mock_slides.side_effect = lambda i: mock_slide  # COM 索引: Slides(1), Slides(2)

    mock_pres = MagicMock()
    mock_pres.Slides = mock_slides

    mock_app = MagicMock()
    mock_app.Presentations.Open.return_value = mock_pres

    with patch("win32com.client.Dispatch", return_value=mock_app):
        result = render_slides(ppt, out_dir)

    assert len(result) == 2
    assert all(p.exists() for p in result)
    assert result[0].name == "slide_000.png"
    assert result[1].name == "slide_001.png"
    mock_pres.Close.assert_called_once()
    mock_app.Quit.assert_called_once()
