"""测试 TTS 引擎"""

import pytest


def test_synthesize_speech(tmp_path):
    """测试语音合成（需要网络）"""
    from src.tts.engine import synthesize_speech

    out = tmp_path / "test.mp3"
    result = synthesize_speech("这是一个测试。", out)

    assert out.exists()
    assert result.audio_path == str(out)
    assert result.duration_seconds > 0


def test_synthesize_speech_with_rate(tmp_path):
    """测试语速调节"""
    from src.tts.engine import synthesize_speech

    out_fast = tmp_path / "fast.mp3"

    synthesize_speech("这是测试文本。", out_fast, rate="+50%")

    assert out_fast.exists()


def test_synthesize_speech_with_voice(tmp_path):
    """测试不同语音角色"""
    from src.tts.engine import synthesize_speech

    out = tmp_path / "voice.mp3"
    result = synthesize_speech("测试语音切换。", out, voice="zh-CN-XiaoxiaoNeural")
    assert out.exists()


def test_synthesize_pages(tmp_path):
    """测试批量页面合成"""
    from pathlib import Path
    from src.tts.engine import synthesize_pages

    pages = {0: "第一页内容。"}
    results = synthesize_pages(pages, tmp_path)

    assert len(results) == 1
    assert 0 in results
    assert Path(results[0].audio_path).exists()


def test_synthesize_short_text(tmp_path):
    """测试短文本自动补足"""
    from src.tts.engine import synthesize_speech

    out = tmp_path / "short.mp3"
    result = synthesize_speech("短", out)
    assert out.exists()
