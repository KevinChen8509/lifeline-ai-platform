"""测试封面生成模块"""

import subprocess
from pathlib import Path

import pytest


def _has_ffmpeg():
    return subprocess.run(["ffmpeg", "-version"], capture_output=True).returncode == 0


def _make_test_video(path: Path, duration: float = 1.0):
    subprocess.run([
        "ffmpeg", "-y",
        "-f", "lavfi", "-i", f"color=c=green:s=320x240:d={duration}",
        "-f", "lavfi", "-i", f"anullsrc=r=22050:cl=mono",
        "-t", str(duration),
        "-c:v", "libx264", "-c:a", "aac",
        "-pix_fmt", "yuv420p", str(path)
    ], capture_output=True)


def _make_test_image(path: Path):
    subprocess.run([
        "ffmpeg", "-y", "-f", "lavfi",
        "-i", "color=c=blue:s=640x480:d=1",
        "-frames:v", "1", str(path)
    ], capture_output=True)


@pytest.mark.skipif(not _has_ffmpeg(), reason="需要 FFmpeg")
def test_generate_cover(tmp_output):
    """测试从视频提取封面"""
    from src.avatar.cover import generate_cover

    video = tmp_output / "test.mp4"
    _make_test_video(video)

    out = tmp_output / "cover.png"
    result = generate_cover(video, output_path=out)
    assert result.exists()
    assert result.stat().st_size > 0


@pytest.mark.skipif(not _has_ffmpeg(), reason="需要 FFmpeg")
def test_generate_cover_auto_output(tmp_output):
    """测试自动输出路径"""
    from src.avatar.cover import generate_cover

    video = tmp_output / "test.mp4"
    _make_test_video(video)

    result = generate_cover(video)
    assert result == video.with_suffix(".png")
    assert result.exists()


@pytest.mark.skipif(not _has_ffmpeg(), reason="需要 FFmpeg")
def test_generate_slideshow_cover(tmp_output):
    """测试幻灯片封面"""
    from src.avatar.cover import generate_slideshow_cover

    img1 = tmp_output / "slide_0.png"
    img2 = tmp_output / "slide_1.png"
    _make_test_image(img1)
    _make_test_image(img2)

    out = tmp_output / "cover.png"
    result = generate_slideshow_cover([img1, img2], output_path=out)
    assert result.exists()
    assert result.stat().st_size > 0


def test_generate_slideshow_cover_empty():
    """测试空列表应报错"""
    from src.avatar.cover import generate_slideshow_cover

    with pytest.raises(RuntimeError, match="没有幻灯片"):
        generate_slideshow_cover([])
