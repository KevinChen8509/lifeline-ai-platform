"""测试画中画合成模块"""

import subprocess
from pathlib import Path
from unittest.mock import patch, MagicMock

import pytest

from src.avatar.compositor import (
    composite_pip,
    composite_pages,
    _get_resolution,
    RESOLUTION_PRESETS,
)


def _has_ffmpeg():
    return subprocess.run(["ffmpeg", "-version"], capture_output=True).returncode == 0


def _make_test_image(path: Path, w: int = 320, h: int = 240):
    """用 FFmpeg 生成测试图片"""
    subprocess.run([
        "ffmpeg", "-y",
        "-f", "lavfi", "-i", f"color=c=blue:s={w}x{h}:d=0.04",
        "-frames:v", "1", str(path),
    ], capture_output=True)


def _make_test_video(path: Path, duration: float = 2.0):
    """用 FFmpeg 生成测试视频"""
    subprocess.run([
        "ffmpeg", "-y",
        "-f", "lavfi", "-i", f"color=c=red:s=256x256:d={duration}",
        "-f", "lavfi", "-i", f"anullsrc=r=22050:cl=mono",
        "-t", str(duration),
        "-c:v", "libx264", "-c:a", "aac",
        "-pix_fmt", "yuv420p", str(path),
    ], capture_output=True)


class TestGetResolution:
    def test_presets(self):
        assert _get_resolution("720p") == (1280, 720)
        assert _get_resolution("1080p") == (1920, 1080)
        assert _get_resolution("4K") == (3840, 2160)
        assert _get_resolution("480p") == (854, 480)

    def test_custom_format(self):
        assert _get_resolution("640x480") == (640, 480)
        assert _get_resolution("1920x1080") == (1920, 1080)

    def test_invalid_fallback(self):
        assert _get_resolution("invalid") == (1280, 720)
        assert _get_resolution("") == (1280, 720)

    def test_resolution_presets_keys(self):
        assert "720p" in RESOLUTION_PRESETS
        assert "1080p" in RESOLUTION_PRESETS
        assert "4K" in RESOLUTION_PRESETS


@pytest.mark.skipif(not _has_ffmpeg(), reason="需要 FFmpeg")
class TestCompositePip:
    def test_basic_pip(self, tmp_path):
        """基本画中画合成"""
        slide = tmp_path / "slide.png"
        video = tmp_path / "avatar.mp4"
        output = tmp_path / "pip.mp4"
        _make_test_image(slide)
        _make_test_video(video)

        result = composite_pip(slide, video, output)
        assert result.exists()
        assert result == output

    def test_pip_creates_output_dir(self, tmp_path):
        """自动创建输出目录"""
        slide = tmp_path / "slide.png"
        video = tmp_path / "avatar.mp4"
        output = tmp_path / "nested" / "dir" / "pip.mp4"
        _make_test_image(slide)
        _make_test_video(video)

        result = composite_pip(slide, video, output)
        assert result.exists()

    def test_pip_resolution(self, tmp_path):
        """不同分辨率的 PIP 合成"""
        slide = tmp_path / "slide.png"
        video = tmp_path / "avatar.mp4"
        _make_test_image(slide, 1280, 720)
        _make_test_video(video)

        for res in ["720p", "480p"]:
            output = tmp_path / f"pip_{res}.mp4"
            result = composite_pip(slide, video, output, resolution=res)
            assert result.exists()

    def test_pip_custom_avatar_size(self, tmp_path):
        """自定义头像窗口大小"""
        slide = tmp_path / "slide.png"
        video = tmp_path / "avatar.mp4"
        output = tmp_path / "pip_custom.mp4"
        _make_test_image(slide)
        _make_test_video(video)

        result = composite_pip(slide, video, output, avatar_size=200, margin=10)
        assert result.exists()


@pytest.mark.skipif(not _has_ffmpeg(), reason="需要 FFmpeg")
class TestCompositePages:
    def test_single_page(self, tmp_path):
        """单页合成"""
        slide = tmp_path / "slides" / "slide_000.png"
        slide.parent.mkdir(parents=True)
        _make_test_image(slide)
        video = tmp_path / "video" / "page_000.mp4"
        video.parent.mkdir(parents=True)
        _make_test_video(video)

        pip_dir = tmp_path / "pip"
        results = composite_pages([slide], {0: video}, pip_dir)
        assert 0 in results
        assert results[0].exists()

    def test_multi_page(self, tmp_path):
        """多页合成"""
        slides = []
        videos = {}
        for i in range(3):
            s = tmp_path / "slides" / f"slide_{i:03d}.png"
            s.parent.mkdir(parents=True, exist_ok=True)
            _make_test_image(s)
            slides.append(s)

            v = tmp_path / "video" / f"page_{i:03d}.mp4"
            v.parent.mkdir(parents=True, exist_ok=True)
            _make_test_video(v)
            videos[i] = v

        pip_dir = tmp_path / "pip"
        results = composite_pages(slides, videos, pip_dir)
        assert len(results) == 3
        for i in range(3):
            assert i in results
            assert results[i].exists()

    def test_parallel(self, tmp_path):
        """并行合成"""
        slides = []
        videos = {}
        for i in range(3):
            s = tmp_path / "slides" / f"slide_{i:03d}.png"
            s.parent.mkdir(parents=True, exist_ok=True)
            _make_test_image(s)
            slides.append(s)

            v = tmp_path / "video" / f"page_{i:03d}.mp4"
            v.parent.mkdir(parents=True, exist_ok=True)
            _make_test_video(v)
            videos[i] = v

        pip_dir = tmp_path / "pip"
        results = composite_pages(slides, videos, pip_dir, parallel=True, max_workers=2)
        assert len(results) == 3

    def test_more_slides_than_videos(self, tmp_path):
        """幻灯片多于视频时，使用最后一张"""
        slides = []
        for i in range(5):
            s = tmp_path / "slides" / f"slide_{i:03d}.png"
            s.parent.mkdir(parents=True, exist_ok=True)
            _make_test_image(s)
            slides.append(s)

        v = tmp_path / "video" / "page_000.mp4"
        v.parent.mkdir(parents=True, exist_ok=True)
        _make_test_video(v)

        pip_dir = tmp_path / "pip"
        results = composite_pages(slides, {0: v}, pip_dir)
        assert 0 in results
        assert results[0].exists()
