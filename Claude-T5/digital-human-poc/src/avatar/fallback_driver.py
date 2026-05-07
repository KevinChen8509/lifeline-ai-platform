"""数字人驱动 - 备用方案（当 SadTalker 不可用时，生成静态图片+音频的视频）"""

from pathlib import Path
from src.config import AVATAR_SOURCE_IMAGE, VIDEO_DIR


def generate_fallback_video(
    audio_path: str | Path,
    source_image: str | Path | None = None,
    output_path: str | Path | None = None,
) -> Path:
    """
    备用方案：用 FFmpeg 将静态头像图片 + 音频合成为视频
    效果是静态图片配语音，作为 SadTalker 不可用时的降级方案
    """
    audio_path = Path(audio_path)
    source_image = Path(source_image or AVATAR_SOURCE_IMAGE)
    output_path = Path(output_path or VIDEO_DIR / f"{audio_path.stem}.mp4")

    if not source_image.exists():
        raise FileNotFoundError(f"头像图片不存在: {source_image}")

    output_path.parent.mkdir(parents=True, exist_ok=True)

    import subprocess
    cmd = [
        "ffmpeg", "-y",
        "-loop", "1",
        "-i", str(source_image),
        "-i", str(audio_path),
        "-c:v", "libx264",
        "-tune", "stillimage",
        "-c:a", "aac",
        "-b:a", "192k",
        "-pix_fmt", "yuv420p",
        "-shortest",
        str(output_path),
    ]

    print(f"  FFmpeg 合成静态视频: {output_path.name}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"FFmpeg 执行失败:\n{result.stderr}")

    return output_path
