"""测试水印模块"""

import subprocess
from pathlib import Path

import pytest


def _has_ffmpeg():
    return subprocess.run(["ffmpeg", "-version"], capture_output=True).returncode == 0


def _make_test_video(path: Path, duration: float = 1.0):
    subprocess.run([
        "ffmpeg", "-y",
        "-f", "lavfi", "-i", f"color=c=blue:s=320x240:d={duration}",
        "-f", "lavfi", "-i", f"anullsrc=r=22050:cl=mono",
        "-t", str(duration),
        "-c:v", "libx264", "-c:a", "aac",
        "-pix_fmt", "yuv420p", str(path)
    ], capture_output=True)


@pytest.mark.skipif(not _has_ffmpeg(), reason="需要 FFmpeg")
def test_text_watermark(tmp_output):
    """测试文字水印"""
    from src.avatar.watermark import add_text_watermark

    video = tmp_output / "test.mp4"
    _make_test_video(video)

    out = tmp_output / "wm.mp4"
    result = add_text_watermark(video, text="测试水印", output_path=out)
    assert result.exists()
    assert result.stat().st_size > 0


@pytest.mark.skipif(not _has_ffmpeg(), reason="需要 FFmpeg")
def test_text_watermark_positions(tmp_output):
    """测试不同位置的水印"""
    from src.avatar.watermark import add_text_watermark

    video = tmp_output / "test.mp4"
    _make_test_video(video)

    for pos in ["top-left", "top-right", "bottom-left", "bottom-right", "center"]:
        out = tmp_output / f"wm_{pos}.mp4"
        result = add_text_watermark(video, text="测试", output_path=out, position=pos)
        assert result.exists()


@pytest.mark.skipif(not _has_ffmpeg(), reason="需要 FFmpeg")
def test_image_watermark(tmp_output):
    """测试图片水印"""
    from src.avatar.watermark import add_image_watermark

    video = tmp_output / "test.mp4"
    _make_test_video(video)

    # 创建一个简单水印图片
    wm_img = tmp_output / "logo.png"
    subprocess.run([
        "ffmpeg", "-y", "-f", "lavfi",
        "-i", "color=c=red:s=64x64:d=1",
        "-frames:v", "1", str(wm_img)
    ], capture_output=True)

    out = tmp_output / "img_wm.mp4"
    result = add_image_watermark(video, image_path=wm_img, output_path=out)
    assert result.exists()
    assert result.stat().st_size > 0
