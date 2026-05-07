"""视频封面/缩略图生成 - 提取关键帧作为封面"""

import subprocess
from pathlib import Path


def generate_cover(
    video_path: str | Path,
    output_path: str | Path | None = None,
    time_offset: float = 0.5,
    width: int = 1280,
    height: int = 720,
) -> Path:
    """
    从视频中提取一帧作为封面图

    Args:
        video_path: 输入视频路径
        output_path: 输出图片路径，None 则自动生成
        time_offset: 提取时间点（秒），默认取 0.5s 处
        width: 输出宽度
        height: 输出高度
    """
    video_path = Path(video_path)
    if output_path is None:
        output_path = video_path.with_suffix(".png")
    else:
        output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    cmd = [
        "ffmpeg", "-y",
        "-i", str(video_path),
        "-ss", str(time_offset),
        "-frames:v", "1",
        "-vf", f"scale={width}:{height}:force_original_aspect_ratio=decrease,pad={width}:{height}:(ow-iw)/2:(oh-ih)/2",
        str(output_path),
    ]

    print(f"  生成封面: {output_path.name} (offset={time_offset}s)")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"封面生成失败:\n{result.stderr}")

    return output_path


def generate_slideshow_cover(
    slide_images: list[Path],
    output_path: str | Path | None = None,
    width: int = 1280,
    height: int = 720,
) -> Path:
    """
    将第一张幻灯片图片作为封面（更高清）

    Args:
        slide_images: 幻灯片图片列表
        output_path: 输出路径
        width: 宽度
        height: 高度
    """
    if not slide_images:
        raise RuntimeError("没有幻灯片图片")

    first = Path(slide_images[0])
    if output_path is None:
        output_path = first.parent / "cover.png"
    else:
        output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    cmd = [
        "ffmpeg", "-y",
        "-i", str(first),
        "-vf", f"scale={width}:{height}:force_original_aspect_ratio=decrease,pad={width}:{height}:(ow-iw)/2:(oh-ih)/2",
        str(output_path),
    ]

    print(f"  生成幻灯片封面: {output_path.name}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"封面生成失败:\n{result.stderr}")

    return output_path
