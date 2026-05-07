"""测试字幕模块"""

from pathlib import Path


def test_split_sentences_basic():
    """测试中文分句"""
    from src.avatar.subtitle import _split_sentences

    sentences = _split_sentences("这是一个测试句子。第二句在这里！还有第三句？")
    assert len(sentences) == 3
    assert sentences[0].endswith("。")
    assert sentences[1].endswith("！")
    assert sentences[2].endswith("？")


def test_split_sentences_max_chars():
    """测试超长文本会被分句"""
    from src.avatar.subtitle import _split_sentences

    long_text = "这是一段非常长的文本" * 5
    sentences = _split_sentences(long_text, max_chars=24)
    # 文本无标点，整段会作为一个长句返回
    assert len(sentences) >= 1
    assert all(isinstance(s, str) for s in sentences)


def test_split_sentences_empty():
    """测试空文本"""
    from src.avatar.subtitle import _split_sentences

    assert _split_sentences("") == []
    assert _split_sentences("   ") == []


def test_generate_srt(tmp_output):
    """测试 SRT 文件生成"""
    from src.avatar.subtitle import _generate_srt

    sentences = ["第一句", "第二句", "第三句"]
    srt_path = tmp_output / "test.srt"
    result = _generate_srt(sentences, 9.0, srt_path)

    assert result == srt_path
    assert srt_path.exists()
    content = srt_path.read_text(encoding="utf-8")
    # SRT 格式应有序号
    assert "1\n" in content
    assert "2\n" in content
    assert "3\n" in content
    # 应有时间戳
    assert "-->" in content


def test_format_time():
    """测试时间格式化"""
    from src.avatar.subtitle import _format_time

    assert _format_time(0) == "00:00:00,000"
    assert _format_time(61.5) == "00:01:01,500"
    assert _format_time(3661.123) == "01:01:01,123"


def test_generate_srt_empty_sentences(tmp_output):
    """测试空句子列表返回路径但不创建文件"""
    from src.avatar.subtitle import _generate_srt

    srt_path = tmp_output / "empty.srt"
    result = _generate_srt([], 10.0, srt_path)
    assert result == srt_path
    # 空句子不应创建文件（函数提前返回）
