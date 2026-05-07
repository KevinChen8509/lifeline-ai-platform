"""测试 PPT 解析模块"""

from pathlib import Path


def test_parse_ppt(sample_ppt):
    """测试 PPT 解析能正常提取幻灯片"""
    from src.parsers.ppt_parser import parse_ppt

    slides = parse_ppt(sample_ppt)
    assert len(slides) > 0, "应至少解析出 1 页"
    for s in slides:
        assert hasattr(s, "slide_index")
        assert hasattr(s, "title")
        assert hasattr(s, "body_texts")
        assert isinstance(s.body_texts, list)


def test_slides_to_markdown(sample_ppt):
    """测试幻灯片转 Markdown"""
    from src.parsers.ppt_parser import parse_ppt, slides_to_markdown

    slides = parse_ppt(sample_ppt)
    md = slides_to_markdown(slides)
    assert isinstance(md, str)
    assert len(md) > 0
    # Markdown 应包含页码标题
    assert "## 第" in md or "第" in md


def test_slides_to_json(sample_ppt):
    """测试幻灯片转 JSON"""
    from src.parsers.ppt_parser import parse_ppt, slides_to_json
    import json

    slides = parse_ppt(sample_ppt)
    json_str = slides_to_json(slides)
    data = json.loads(json_str)
    assert isinstance(data, list)
    assert len(data) == len(slides)


def test_parse_nonexistent_file():
    """测试解析不存在的文件应抛出异常"""
    from src.parsers.ppt_parser import parse_ppt

    import pytest
    with pytest.raises(FileNotFoundError):
        parse_ppt(Path("nonexistent.pptx"))
