"""视频合成 - 将 PPT 幻灯片图片和数字人视频合成为画中画布局"""

import subprocess
from pathlib import Path


def composite_pip(
    ppt_image: str | Path,
    avatar_video: str | Path,
    output_path: str | Path,
    avatar_size: int = 320,
    margin: int = 20,
) -> Path:
    """
    画中画合成: PPT 幻灯片全屏 + 数字人视频叠加在右下角

    Args:
        ppt_image: PPT 幻灯片图片路径
        avatar_video: 数字人视频路径
        output_path: 输出视频路径
        avatar_size: 数字人窗口尺寸（像素）
        margin: 数字人窗口距边缘间距
    """
    ppt_image = Path(ppt_image)
    avatar_video = Path(avatar_video)
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    # FFmpeg 画中画合成
    # [0:v] PPT 图片 (loop) → 缩放到 1280x720 作为背景
    # [1:v] 数字人视频 → 缩放为 avatar_size 作为前景
    # overlay 叠加到右下角
    cmd = [
        "ffmpeg", "-y",
        "-loop", "1",
        "-i", str(ppt_image),         # [0] PPT 图片
        "-i", str(avatar_video),       # [1] 数字人视频
        "-filter_complex",
        (
            f"[0:v]scale=1280:720:force_original_aspect_ratio=decrease,"
            f"pad=1280:720:(ow-iw)/2:(oh-ih)/2,format=yuv420p[bg];"
            f"[1:v]scale={avatar_size}:-1,format=yuv420p[fg];"
            f"[bg][fg]overlay=W-w-{margin}:H-h-{margin}"
        ),
        "-map", "1:a",                 # 使用数字人视频的音频
        "-c:v", "libx264",
        "-c:a", "aac",
        "-b:a", "192k",
        "-pix_fmt", "yuv420p",
        "-shortest",
        str(output_path),
    ]

    print(f"  合成画中画: {ppt_image.name} + {avatar_video.name}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"画中画合成失败:\n{result.stderr}")

    return output_path


def composite_pages(
    slide_images: list[Path],
    page_videos: dict[int, Path],
    output_dir: str | Path,
) -> dict[int, Path]:
    """
    批量合成每页视频的画中画版本

    Args:
        slide_images: 幻灯片图片列表（按页码顺序）
        page_videos: {页码: 数字人视频路径}
        output_dir: 输出目录

    Returns:
        {页码: 合成后的视频路径}
    """
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    results = {}
    for page_num in sorted(page_videos.keys()):
        if page_num < len(slide_images):
            slide_img = slide_images[page_num]
        else:
            # 页码超出幻灯片数量，使用最后一页
            slide_img = slide_images[-1]

        out = output_dir / f"pip_page_{page_num:03d}.mp4"
        results[page_num] = composite_pip(
            ppt_image=slide_img,
            avatar_video=page_videos[page_num],
            output_path=out,
        )

    return results
