"""视频水印工具 - 支持文字水印和图片水印"""

import shutil
import subprocess
import sys
from pathlib import Path

# Windows 字体候选列表
_WIN_FONTS = ["simhei.ttf", "msyh.ttc", "msyhbd.ttc", "arial.ttf"]


def _find_font(output_dir: Path) -> str | None:
    """查找系统可用字体，复制到输出目录"""
    if sys.platform != "win32":
        return None
    fonts_dir = Path("C:/Windows/Fonts")
    for name in _WIN_FONTS:
        src = fonts_dir / name
        if src.exists():
            ext = Path(name).suffix
            dst = output_dir / f"_wm_font{ext}"
            if not dst.exists():
                shutil.copy2(src, dst)
            return dst.name
    return None


def add_text_watermark(
    video_path: str | Path,
    text: str = "数字人汇报",
    output_path: str | Path | None = None,
    position: str = "bottom-right",
    font_size: int = 24,
    opacity: float = 0.5,
    color: str = "white",
) -> Path:
    """
    添加文字水印

    Args:
        video_path: 输入视频路径
        text: 水印文字
        output_path: 输出路径，None 则自动生成
        position: 位置 (top-left/top-right/bottom-left/bottom-right/center)
        font_size: 字号
        opacity: 透明度 (0.0~1.0)
        color: 文字颜色
    """
    video_path = Path(video_path)
    if output_path is None:
        output_path = video_path.parent / f"{video_path.stem}_wm{video_path.suffix}"
    else:
        output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    margin = 20
    positions = {
        "top-left": f"x={margin}:y={margin}",
        "top-right": f"x=w-text_w-{margin}:y={margin}",
        "bottom-left": f"x={margin}:y=h-text_h-{margin}",
        "bottom-right": f"x=w-text_w-{margin}:y=h-text_h-{margin}",
        "center": "x=(w-text_w)/2:y=(h-text_h)/2",
    }
    pos_expr = positions.get(position, positions["bottom-right"])

    safe_text = text.replace("'", "\\'").replace(":", "\\:")

    font = _find_font(output_path.parent)
    font_param = f":fontfile={font}" if font else ""

    filter_str = (
        f"drawtext=text='{safe_text}'"
        f":fontsize={font_size}"
        f":fontcolor={color}@{opacity}"
        f"{font_param}"
        f":{pos_expr}"
        ":shadowcolor=black@0.5:shadowx=1:shadowy=1"
    )

    cmd = [
        "ffmpeg", "-y",
        "-i", str(video_path.resolve()),
        "-vf", filter_str,
        "-c:a", "copy",
        "-c:v", "libx264",
        "-pix_fmt", "yuv420p",
        str(output_path.resolve()),
    ]

    # 在输出目录中执行，让字体相对路径生效
    cwd = str(output_path.parent)

    print(f"  添加文字水印: \"{text}\" -> {output_path.name}")
    result = subprocess.run(cmd, capture_output=True, text=True, cwd=cwd)
    if result.returncode != 0:
        raise RuntimeError(f"水印添加失败:\n{result.stderr}")

    return output_path


def add_image_watermark(
    video_path: str | Path,
    image_path: str | Path,
    output_path: str | Path | None = None,
    position: str = "bottom-right",
    scale: int = 80,
    opacity: float = 0.7,
) -> Path:
    """
    添加图片水印（Logo）

    Args:
        video_path: 输入视频路径
        image_path: 水印图片路径
        output_path: 输出路径
        position: 位置
        scale: 水印宽度（像素）
        opacity: 透明度
    """
    video_path = Path(video_path)
    image_path = Path(image_path)
    if output_path is None:
        output_path = video_path.parent / f"{video_path.stem}_wm{video_path.suffix}"
    else:
        output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    margin = 20
    positions = {
        "top-left": f"{margin}:{margin}",
        "top-right": f"W-w-{margin}:{margin}",
        "bottom-left": f"{margin}:H-h-{margin}",
        "bottom-right": f"W-w-{margin}:H-h-{margin}",
        "center": "(W-w)/2:(H-h)/2",
    }
    pos_expr = positions.get(position, positions["bottom-right"])

    filter_str = (
        f"[1:v]scale={scale}:-1,format=rgba,colorchannelmixer=aa={opacity}[wm];"
        f"[0:v][wm]overlay={pos_expr}"
    )

    cmd = [
        "ffmpeg", "-y",
        "-i", str(video_path),
        "-i", str(image_path),
        "-filter_complex", filter_str,
        "-c:a", "copy",
        "-c:v", "libx264",
        "-pix_fmt", "yuv420p",
        str(output_path),
    ]

    print(f"  添加图片水印: {image_path.name} -> {output_path.name}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"图片水印添加失败:\n{result.stderr}")

    return output_path
