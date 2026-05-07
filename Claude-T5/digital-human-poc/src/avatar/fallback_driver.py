"""数字人驱动 - 备用方案（当 SadTalker 不可用时，生成静态图片+音频的视频）"""

from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from src.config import AVATAR_SOURCE_IMAGE, VIDEO_DIR


def generate_fallback_video(
    audio_path: str | Path,
    source_image: str | Path | None = None,
    output_path: str | Path | None = None,
) -> Path:
    """
    备用方案：用 FFmpeg 将静态头像图片 + 音频合成为视频
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


def generate_fallback_pages_parallel(
    audio_results: dict[int, "TTSResult"],
    source_image: str | Path | None = None,
    output_dir: str | Path | None = None,
    max_workers: int = 3,
) -> dict[int, Path]:
    """
    并行生成多页静态视频

    Args:
        audio_results: {页码: TTSResult}
        source_image: 头像图片
        output_dir: 输出目录
        max_workers: 并行数（建议不超过 CPU 核心数）
    """
    if output_dir is None:
        output_dir = VIDEO_DIR
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    def _gen(item):
        pn, tts_result = item
        out_path = output_dir / f"page_{pn:03d}.mp4"
        video = generate_fallback_video(
            audio_path=tts_result.audio_path,
            source_image=source_image,
            output_path=out_path,
        )
        return pn, video

    results = {}
    with ThreadPoolExecutor(max_workers=max_workers) as pool:
        for pn, video_path in pool.map(_gen, audio_results.items()):
            results[pn] = video_path

    return results
