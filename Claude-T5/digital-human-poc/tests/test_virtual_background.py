"""测试虚拟背景模块"""

import subprocess
from pathlib import Path

import pytest

from src.avatar.virtual_background import (
    BUILTIN_BACKGROUNDS,
    apply_virtual_background,
    apply_background_to_pages,
    _probe_size,
)


def _has_ffmpeg():
    return subprocess.run(["ffmpeg", "-version"], capture_output=True).returncode == 0


def _make_test_image(path: Path):
    """创建测试图片"""
    subprocess.run([
        "ffmpeg", "-y",
        "-f", "lavfi", "-i", "color=c=green:s=128x128:d=1",
        "-frames:v", "1", str(path),
    ], capture_output=True)


def test_builtin_backgrounds():
    """内置预设应包含选项"""
    assert "无（保留原背景）" in BUILTIN_BACKGROUNDS
    assert BUILTIN_BACKGROUNDS["无（保留原背景）"] is None
    assert BUILTIN_BACKGROUNDS["纯白"] == "#FFFFFF"


def test_apply_no_background(tmp_path):
    """None 背景应直接拷贝"""
    src = tmp_path / "src.png"
    src.write_bytes(b"\x89PNG" + b"\x00" * 50)
    out = tmp_path / "out.png"
    result = apply_virtual_background(src, out, background=None)
    assert result == out
    assert out.exists()


def test_apply_no_background_same_path(tmp_path):
    """None 背景且路径相同时直接返回"""
    src = tmp_path / "src.png"
    src.write_bytes(b"\x89PNG" + b"\x00" * 50)
    result = apply_virtual_background(src, src, background=None)
    assert result == src


@pytest.mark.skipif(not _has_ffmpeg(), reason="需要 FFmpeg")
def test_apply_solid_background(tmp_path):
    """纯色背景替换"""
    src = tmp_path / "green.png"
    _make_test_image(src)
    out = tmp_path / "white.png"
    result = apply_virtual_background(src, out, background="纯白")
    assert result.exists()
    assert result.stat().st_size > 0


@pytest.mark.skipif(not _has_ffmpeg(), reason="需要 FFmpeg")
def test_apply_gradient_background(tmp_path):
    """渐变背景替换"""
    src = tmp_path / "green.png"
    _make_test_image(src)
    out = tmp_path / "gradient.png"
    result = apply_virtual_background(src, out, background="深蓝渐变")
    assert result.exists()
    assert result.stat().st_size > 0


@pytest.mark.skipif(not _has_ffmpeg(), reason="需要 FFmpeg")
def test_probe_size(tmp_path):
    """探测图片分辨率"""
    src = tmp_path / "test.png"
    _make_test_image(src)
    size = _probe_size(src)
    assert "x" in size
    assert "128" in size


def test_apply_background_to_pages_no_bg():
    """无背景时批量处理应返回原路径"""
    pages = {0: Path("/fake/a.mp4"), 1: Path("/fake/b.mp4")}
    result = apply_background_to_pages(pages, "/tmp/out", background=None)
    assert result == pages


def test_apply_nonexistent_background_fallback(tmp_path):
    """不存在的背景文件应降级为拷贝"""
    src = tmp_path / "src.png"
    src.write_bytes(b"\x89PNG" + b"\x00" * 50)
    out = tmp_path / "out.png"
    result = apply_virtual_background(src, out, background="/nonexistent/file.png")
    assert result.exists()
