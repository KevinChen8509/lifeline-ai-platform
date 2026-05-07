"""语言检测 - 根据 PPT 内容判断主要语言"""

import re


def detect_language(text: str) -> str:
    """
    检测文本的主要语言

    Returns:
        "zh" (中文) 或 "en" (英文)
    """
    # 统计中文字符数
    chinese_chars = len(re.findall(r'[\u4e00-\u9fff]', text))
    # 统计英文单词数
    english_words = len(re.findall(r'[a-zA-Z]+', text))

    if chinese_chars > english_words:
        return "zh"
    return "en"


def get_default_voice(language: str) -> str:
    """根据语言返回默认 TTS 语音"""
    if language == "en":
        return "en-US-AndrewNeural"
    return "zh-CN-YunxiNeural"
