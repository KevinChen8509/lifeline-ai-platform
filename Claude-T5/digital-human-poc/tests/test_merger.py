"""测试视频合并模块"""

import subprocess
from pathlib import Path

import pytest


def _has_ffmpeg():
    return subprocess.run(["ffmpeg", "-version"], capture_output=True).returncode == 0


def _make_test_video(path: Path, duration: float = 1.0):
    """生成一个测试视频（纯黑+静音）"""
    subprocess.run([
        "ffmpeg", "-y",
        "-f", "lavfi", "-i", f"color=c=black:s=256x256:d={duration}",
        "-f", "lavfi", "-i", f"anullsrc=r=22050:cl=mono",
        "-t", str(duration),
        "-c:v", "libx264", "-c:a", "aac",
        "-pix_fmt", "yuv420p", str(path)
    ], capture_output=True)


@pytest.mark.skipif(not _has_ffmpeg(), reason="需要 FFmpeg")
def test_merge_concat(tmp_output):
    """测试直接拼接"""
    from src.avatar.merger import merge_videos

    v1 = tmp_output / "v1.mp4"
    v2 = tmp_output / "v2.mp4"
    _make_test_video(v1, 1.0)
    _make_test_video(v2, 1.0)

    out = tmp_output / "merged.mp4"
    result = merge_videos([v1, v2], out, transition="none")
    assert result.exists()
    assert result.stat().st_size > 0


@pytest.mark.skipif(not _has_ffmpeg(), reason="需要 FFmpeg")
def test_merge_xfade(tmp_output):
    """测试 xfade 转场合并"""
    from src.avatar.merger import merge_videos

    v1 = tmp_output / "v1.mp4"
    v2 = tmp_output / "v2.mp4"
    _make_test_video(v1, 2.0)
    _make_test_video(v2, 2.0)

    out = tmp_output / "xfade.mp4"
    result = merge_videos([v1, v2], out, transition="fade", transition_duration=0.5)
    assert result.exists()
    assert result.stat().st_size > 0


@pytest.mark.skipif(not _has_ffmpeg(), reason="需要 FFmpeg")
def test_merge_single_video(tmp_output):
    """测试单视频直接拷贝"""
    from src.avatar.merger import merge_videos

    v1 = tmp_output / "v1.mp4"
    _make_test_video(v1, 1.0)

    out = tmp_output / "single.mp4"
    result = merge_videos([v1], out)
    assert result.exists()


def test_merge_nonexistent_raises():
    """测试合并不存在的视频应报错"""
    from src.avatar.merger import merge_videos

    with pytest.raises(RuntimeError):
        merge_videos([Path("nonexistent.mp4")], Path("out.mp4"))
