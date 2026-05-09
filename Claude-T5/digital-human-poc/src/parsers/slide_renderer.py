"""PPT 幻灯片渲染 - 将 PPT 页面导出为图片

优先使用 PowerPoint COM (pywin32)，若不可用则降级为 LibreOffice headless 模式。
"""

import os
import shutil
import subprocess
from pathlib import Path
from src.logger import get_logger

log = get_logger(__name__)


def _find_libreoffice() -> str | None:
    """查找 LibreOffice 可执行文件路径"""
    # 常见安装路径（Windows / Linux / macOS）
    candidates = [
        shutil.which("libreoffice"),
        shutil.which("soffice"),
        r"C:\Program Files\LibreOffice\program\soffice.exe",
        r"C:\Program Files (x86)\LibreOffice\program\soffice.exe",
        "/usr/bin/libreoffice",
        "/usr/bin/soffice",
        "/Applications/LibreOffice.app/Contents/MacOS/soffice",
    ]
    for p in candidates:
        if p and os.path.isfile(p):
            return p
    return None


def _render_libreoffice(
    ppt_path: Path,
    output_dir: Path,
    width: int,
    height: int,
) -> list[Path]:
    """使用 LibreOffice headless 模式导出幻灯片为 PNG"""
    soffice = _find_libreoffice()
    if not soffice:
        raise RuntimeError(
            "LibreOffice 未安装。请安装 LibreOffice 或 Microsoft PowerPoint。\n"
            "下载: https://www.libreoffice.org/download/"
        )

    log.info("  使用 LibreOffice 渲染: %s", soffice)

    # LibreOffice 导出为 PDF，再用 FFmpeg 转为图片
    # （LO 不支持直接导出指定分辨率的 PNG）
    tmp_dir = output_dir / "_lo_tmp"
    tmp_dir.mkdir(parents=True, exist_ok=True)

    try:
        # 导出为 PDF
        cmd = [
            soffice, "--headless", "--convert-to", "pdf",
            "--outdir", str(tmp_dir), str(ppt_path),
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        if result.returncode != 0:
            raise RuntimeError(f"LibreOffice 导出失败: {result.stderr}")

        pdf_file = tmp_dir / f"{ppt_path.stem}.pdf"
        if not pdf_file.exists():
            raise RuntimeError("LibreOffice 未生成 PDF 文件")

        # 用 FFmpeg 将 PDF 页导出为 PNG
        images = []
        page = 0
        while True:
            out_path = output_dir / f"slide_{page:03d}.png"
            cmd = [
                "ffmpeg", "-y",
                "-i", str(pdf_file),
                "-vf", f"scale={width}:{height}:force_original_aspect_ratio=decrease,"
                       f"pad={width}:{height}:(ow-iw)/2:(oh-ih)/2",
                "-frames:v", "1",
                "-an",
                "-c:v", "png",
                f"-page_number", str(page + 1),
                str(out_path),
            ]
            r = subprocess.run(cmd, capture_output=True, text=True)
            if r.returncode != 0 or not out_path.exists():
                break
            images.append(out_path)
            log.info("  导出第 %s 页: %s", page + 1, out_path.name)
            page += 1
            # 安全上限
            if page >= 200:
                break

        if not images:
            raise RuntimeError("未能从 PDF 导出任何幻灯片图片")
        return images
    finally:
        # 清理临时文件
        shutil.rmtree(tmp_dir, ignore_errors=True)


def render_slides(
    ppt_path: str | Path,
    output_dir: str | Path,
    width: int = 1280,
    height: int = 720,
) -> list[Path]:
    """
    将 PPT 每页导出为 PNG 图片

    优先使用 PowerPoint COM（pywin32），若不可用则降级为 LibreOffice。

    Args:
        ppt_path: PPT 文件路径
        output_dir: 输出目录
        width: 导出图片宽度
        height: 导出图片高度

    Returns:
        导出的 PNG 文件路径列表（按页码顺序）
    """
    ppt_path = Path(ppt_path).resolve()
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    if not ppt_path.exists():
        raise FileNotFoundError(f"PPT 文件不存在: {ppt_path}")

    # 尝试 PowerPoint COM
    try:
        import win32com.client
    except ImportError:
        log.info("  pywin32 不可用，尝试 LibreOffice...")
        return _render_libreoffice(ppt_path, output_dir, width, height)

    ppt_app = None
    presentation = None
    try:
        ppt_app = win32com.client.Dispatch("PowerPoint.Application")
        presentation = ppt_app.Presentations.Open(
            str(ppt_path),
            WithWindow=False,
        )

        slide_count = presentation.Slides.Count
        images = []

        for i in range(1, slide_count + 1):
            slide = presentation.Slides(i)
            out_path = output_dir / f"slide_{i - 1:03d}.png"
            slide.Export(str(out_path.resolve()), "PNG", width, height)

            if out_path.exists():
                images.append(out_path)
                log.info("  导出第 %s 页: %s", i, out_path.name)
            else:
                log.warning("警告: 第 %s 页导出失败", i)

        return images

    except Exception as e:
        if "PowerPoint" in str(e) or "Dispatch" in str(e):
            log.info("  PowerPoint COM 不可用 (%s)，尝试 LibreOffice...", e)
            return _render_libreoffice(ppt_path, output_dir, width, height)
        raise
    finally:
        if presentation is not None:
            try:
                presentation.Close()
            except Exception:
                pass
        if ppt_app is not None:
            try:
                ppt_app.Quit()
            except Exception:
                pass
