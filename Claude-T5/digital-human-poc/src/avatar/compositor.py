"""视频合成 - 将 PPT 幻灯片图片和数字人视频合成为画中画布局"""

import subprocess
from pathlib import Path

# 分辨率预设
RESOLUTION_PRESETS = {
    "480p": (854, 480),
    "720p": (1280, 720),
    "1080p": (1920, 1080),
    "4K": (3840, 2160),
}


def _get_resolution(resolution: str) -> tuple[int, int]:
    """获取分辨率 (width, height)"""
    if resolution in RESOLUTION_PRESETS:
        return RESOLUTION_PRESETS[resolution]
    # 尝试解析 "1280x720" 格式
    try:
        w, h = resolution.lower().split("x")
        return (int(w), int(h))
    except (ValueError, AttributeError):
        return RESOLUTION_PRESETS["720p"]


def composite_pip(
    ppt_image: str | Path,
    avatar_video: str | Path,
    output_path: str | Path,
    avatar_size: int = 320,
    margin: int = 20,
    resolution: str = "720p",
) -> Path:
    """
    画中画合成: PPT 幻灯片全屏 + 数字人视频叠加在右下角

    Args:
        ppt_image: PPT 幻灯片图片路径
        avatar_video: 数字人视频路径
        output_path: 输出视频路径
        avatar_size: 数字人窗口尺寸（像素）
        margin: 数字人窗口距边缘间距
        resolution: 输出分辨率 ("720p", "1080p", "4K")
    """
    ppt_image = Path(ppt_image)
    avatar_video = Path(avatar_video)
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    w, h = _get_resolution(resolution)

    # 根据分辨率调整头像大小
    if avatar_size == 320:  # 使用默认值时，按比例缩放
        avatar_size = w // 4

    cmd = [
        "ffmpeg", "-y",
        "-loop", "1",
        "-i", str(ppt_image),
        "-i", str(avatar_video),
        "-filter_complex",
        (
            f"[0:v]scale={w}:{h}:force_original_aspect_ratio=decrease,"
            f"pad={w}:{h}:(ow-iw)/2:(oh-ih)/2,format=yuv420p[bg];"
            f"[1:v]scale={avatar_size}:-1,format=yuv420p[fg];"
            f"[bg][fg]overlay=W-w-{margin}:H-h-{margin}"
        ),
        "-map", "1:a",
        "-c:v", "libx264",
        "-c:a", "aac",
        "-b:a", "192k",
        "-pix_fmt", "yuv420p",
        "-shortest",
        str(output_path),
    ]

    print(f"  合成画中画 [{resolution}]: {ppt_image.name} + {avatar_video.name}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"画中画合成失败:\n{result.stderr}")

    return output_path


def composite_pages(
    slide_images: list[Path],
    page_videos: dict[int, Path],
    output_dir: str | Path,
    resolution: str = "720p",
) -> dict[int, Path]:
    """
    批量合成每页视频的画中画版本
    """
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    results = {}
    for page_num in sorted(page_videos.keys()):
        if page_num < len(slide_images):
            slide_img = slide_images[page_num]
        else:
            slide_img = slide_images[-1]

        out = output_dir / f"pip_page_{page_num:03d}.mp4"
        results[page_num] = composite_pip(
            ppt_image=slide_img,
            avatar_video=page_videos[page_num],
            output_path=out,
            resolution=resolution,
        )

    return results
