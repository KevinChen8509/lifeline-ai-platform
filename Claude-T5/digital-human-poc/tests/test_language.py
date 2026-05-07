"""测试语言检测模块"""


def test_detect_chinese():
    from src.detectors.language import detect_language
    assert detect_language("这是一个中文测试文本") == "zh"


def test_detect_english():
    from src.detectors.language import detect_language
    assert detect_language("This is an English test document") == "en"


def test_detect_mixed():
    from src.detectors.language import detect_language
    # 中文占多数
    assert detect_language("这个project使用了Python和AI技术来实现数字人系统") == "zh"
    # 英文占多数
    assert detect_language("The quick brown fox jumps over the lazy dog and runs away") == "en"


def test_get_default_voice():
    from src.detectors.language import get_default_voice
    assert get_default_voice("zh") == "zh-CN-YunxiNeural"
    assert get_default_voice("en") == "en-US-AndrewNeural"
