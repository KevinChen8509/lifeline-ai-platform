"""补充测试 - 边界条件和集成测试"""

import subprocess
from pathlib import Path

import pytest


def _has_ffmpeg():
    return subprocess.run(["ffmpeg", "-version"], capture_output=True).returncode == 0


def _make_test_video(path: Path, duration: float = 1.0):
    subprocess.run([
        "ffmpeg", "-y",
        "-f", "lavfi", "-i", f"color=c=black:s=256x256:d={duration}",
        "-f", "lavfi", "-i", f"anullsrc=r=22050:cl=mono",
        "-t", str(duration),
        "-c:v", "libx264", "-c:a", "aac",
        "-pix_fmt", "yuv420p", str(path)
    ], capture_output=True)


# ============ Compositor 测试 ============

def test_resolution_presets_values():
    """验证分辨率预设值正确"""
    from src.avatar.compositor import RESOLUTION_PRESETS
    assert RESOLUTION_PRESETS["480p"] == (854, 480)
    assert RESOLUTION_PRESETS["720p"] == (1280, 720)
    assert RESOLUTION_PRESETS["1080p"] == (1920, 1080)
    assert RESOLUTION_PRESETS["4K"] == (3840, 2160)


def test_get_resolution_custom():
    """测试自定义分辨率解析"""
    from src.avatar.compositor import _get_resolution
    assert _get_resolution("800x600") == (800, 600)
    assert _get_resolution("invalid") == (1280, 720)  # 回退


def test_composite_pages_empty_slides():
    """测试空幻灯片列表"""
    from src.avatar.compositor import composite_pages
    result = composite_pages([], {}, Path("/tmp/test"))
    assert result == {}


# ============ Merger 测试 ============

def test_transition_choices():
    """验证转场选项完整"""
    from src.avatar.merger import TRANSITION_CHOICES
    assert len(TRANSITION_CHOICES) == 9  # 8 种转场 + 无转场
    assert "淡入淡出 (fade)" in TRANSITION_CHOICES
    assert "无转场" in TRANSITION_CHOICES


@pytest.mark.skipif(not _has_ffmpeg(), reason="需要 FFmpeg")
def test_merge_three_videos(tmp_output):
    """测试 3 个视频合并"""
    from src.avatar.merger import merge_videos
    videos = []
    for i in range(3):
        v = tmp_output / f"v{i}.mp4"
        _make_test_video(v, 1.5)
        videos.append(v)
    out = tmp_output / "merged3.mp4"
    result = merge_videos(videos, out, transition="fade")
    assert result.exists()
    assert result.stat().st_size > 0


# ============ TTS Cache 测试 ============

def test_cache_key_deterministic():
    """测试缓存键确定性"""
    from src.tts.engine import _cache_key
    k1 = _cache_key("hello", "v1", "+0%", "+0Hz", "+0%")
    k2 = _cache_key("hello", "v1", "+0%", "+0Hz", "+0%")
    assert k1 == k2


def test_cache_key_changes_with_params():
    """测试不同参数生成不同键"""
    from src.tts.engine import _cache_key
    k1 = _cache_key("hello", "v1", "+0%", "+0Hz", "+0%")
    k2 = _cache_key("hello", "v1", "+20%", "+0Hz", "+0%")
    k3 = _cache_key("hello", "v2", "+0%", "+0Hz", "+0%")
    assert k1 != k2
    assert k1 != k3


def test_clear_cache_idempotent():
    """清空空缓存不报错"""
    from src.tts.engine import clear_tts_cache
    clear_tts_cache()
    clear_tts_cache()  # 连续清两次


# ============ Pipeline Checkpoint 测试 ============

def test_checkpoint_save_load(tmp_output):
    """测试检查点保存和加载"""
    from src.pipeline import DigitalHumanPipeline
    from src.tts.engine import TTSResult

    pipeline = DigitalHumanPipeline()
    pipeline.output_dir = tmp_output
    pipeline.script_pages = {0: "测试文本"}
    pipeline.audio_results = {
        0: TTSResult(audio_path="/fake/audio.mp3", duration_seconds=5.0)
    }
    pipeline.video_results = {0: Path("/fake/video.mp4")}
    pipeline.slide_images = [Path("/fake/slide.png")]

    pipeline._save_checkpoint("video")

    cp = pipeline._load_checkpoint()
    assert cp is not None
    assert cp["step"] == "video"
    assert "0" in cp["audio_paths"]
    assert "0" in cp["video_paths"]


def test_checkpoint_no_file():
    """没有检查点文件时返回 None"""
    from src.pipeline import DigitalHumanPipeline
    pipeline = DigitalHumanPipeline()
    pipeline.output_dir = Path("/nonexistent")
    assert pipeline._load_checkpoint() is None


# ============ Subtitle 边界测试 ============

def test_subtitle_format_time():
    """测试时间格式化"""
    from src.avatar.subtitle import _format_time
    assert _format_time(0) == "00:00:00,000"
    assert _format_time(61.5) == "00:01:01,500"
    assert _format_time(3600) == "01:00:00,000"


def test_subtitle_split_long_text():
    """测试长文本分句"""
    from src.avatar.subtitle import _split_sentences
    text = "这是第一句。这是第二句。这是第三句。" * 20
    sentences = _split_sentences(text)
    assert len(sentences) > 1


# ============ Watermark 边界测试 ============

def test_watermark_font_detection():
    """测试字体检测不报错"""
    from src.avatar.watermark import _find_font
    # 只要能返回 None 或有效路径即可
    result = _find_font(Path("E:/Agentic/Claude-T5/digital-human-poc/outputs"))
    assert result is None or isinstance(result, str)


# ============ Language Detection 边界测试 ============

def test_language_detection_empty():
    """空文本默认中文"""
    from src.detectors.language import detect_language
    assert detect_language("") == "zh"


def test_language_detection_pure_numbers():
    """纯数字默认中文"""
    from src.detectors.language import detect_language
    assert detect_language("1234567890") == "zh"


# ============ Cover 边界测试 ============

@pytest.mark.skipif(not _has_ffmpeg(), reason="需要 FFmpeg")
def test_cover_custom_offset(tmp_output):
    """测试自定义时间点提取封面"""
    from src.avatar.cover import generate_cover
    video = tmp_output / "test.mp4"
    _make_test_video(video, 3.0)

    out = tmp_output / "cover_2s.png"
    result = generate_cover(video, output_path=out, time_offset=2.0)
    assert result.exists()


# ============ Pipeline 清理测试 ============

def test_pipeline_cleanup(tmp_output):
    """测试中间文件清理"""
    from src.pipeline import DigitalHumanPipeline
    pipeline = DigitalHumanPipeline()
    pipeline.output_dir = tmp_output

    # 创建模拟中间目录
    for d in ("video", "pip", "subtitle", "audio"):
        (tmp_output / d).mkdir(parents=True, exist_ok=True)
        (tmp_output / d / "test.tmp").write_text("temp")

    # 创建应保留的文件
    (tmp_output / "script.txt").write_text("script")
    final = tmp_output / "final.mp4"
    final.write_bytes(b"\x00" * 100)

    pipeline._cleanup(preserve=final)

    # 中间目录应被删除
    assert not (tmp_output / "video").exists()
    assert not (tmp_output / "pip").exists()
    assert not (tmp_output / "subtitle").exists()
    assert not (tmp_output / "audio").exists()
    # 保留文件应存在
    assert (tmp_output / "script.txt").exists()
    assert final.exists()


def test_pipeline_cleanup_no_preserve(tmp_output):
    """测试无保留文件的清理"""
    from src.pipeline import DigitalHumanPipeline
    pipeline = DigitalHumanPipeline()
    pipeline.output_dir = tmp_output
    (tmp_output / "video").mkdir()
    (tmp_output / "video" / "test.tmp").write_text("temp")
    pipeline._cleanup(preserve=None)
    assert not (tmp_output / "video").exists()
