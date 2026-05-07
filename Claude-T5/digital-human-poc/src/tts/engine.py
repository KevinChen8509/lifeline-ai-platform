"""TTS 语音合成引擎 - 支持 Edge TTS"""

import asyncio
from pathlib import Path
from dataclasses import dataclass

import edge_tts

from src.config import TTS_VOICE, TTS_RATE, AUDIO_DIR


@dataclass
class TTSResult:
    """TTS 合成结果"""
    audio_path: str
    duration_seconds: float = 0.0
    voice: str = ""


async def _synthesize(
    text: str,
    output_path: str | Path,
    voice: str = TTS_VOICE,
    rate: str = TTS_RATE,
    pitch: str = "+0Hz",
    volume: str = "+0%",
) -> TTSResult:
    """使用 Edge TTS 合成语音"""
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    communicate = edge_tts.Communicate(text, voice, rate=rate, pitch=pitch, volume=volume)
    await communicate.save(str(output_path))

    # 估算时长（Edge TTS 中文约 4-5 字/秒）
    clean_text = text.replace("[PAUSE]", "").replace("[EMPHASIS]", "")
    char_count = len(clean_text)
    duration = char_count / 4.5  # 粗略估算

    return TTSResult(
        audio_path=str(output_path),
        duration_seconds=duration,
        voice=voice,
    )


def synthesize_speech(
    text: str,
    output_path: str | Path | None = None,
    voice: str | None = None,
    rate: str | None = None,
    pitch: str = "+0Hz",
    volume: str = "+0%",
) -> TTSResult:
    """
    文本转语音

    Args:
        text: 要合成的文本
        output_path: 输出音频路径，None 则自动生成
        voice: 语音角色，None 使用默认
        rate: 语速 (如 "+20%", "-10%")
        pitch: 音调 (如 "+5Hz", "-5Hz")
        volume: 音量 (如 "+10%", "-10%")
    """
    # 清理不适合朗读的标记和特殊字符
    import re
    clean_text = text.replace("[PAUSE]", "...").replace("[EMPHASIS]", "")
    clean_text = clean_text.replace("**", "").replace("---", "")
    # 合并换行为逗号，去除多余空白
    clean_text = re.sub(r'\n+', '，', clean_text)
    clean_text = re.sub(r'\s+', ' ', clean_text).strip()
    # 替换 Edge TTS 不友好的字符
    clean_text = clean_text.replace('/', ' ').replace('\\', ' ')
    clean_text = re.sub(r'[+\-]', ' ', clean_text)
    clean_text = re.sub(r'[（(]', '，', clean_text)
    clean_text = re.sub(r'[）)]', '，', clean_text)
    clean_text = re.sub(r'[:：]', '，', clean_text)
    clean_text = re.sub(r'\s+', ' ', clean_text).strip()
    # 过短文本补足（Edge TTS 要求至少约 20 字符）
    if len(clean_text) < 20:
        clean_text = clean_text + "，以上就是这部分的内容。"

    if output_path is None:
        import hashlib
        fname = hashlib.md5(clean_text.encode()).hexdigest()[:12]
        output_path = AUDIO_DIR / f"{fname}.mp3"

    return asyncio.run(
        _synthesize(clean_text, output_path, voice=voice or TTS_VOICE,
                    rate=rate or TTS_RATE, pitch=pitch, volume=volume)
    )


def synthesize_pages(
    pages: dict[int, str],
    output_dir: str | Path | None = None,
    rate: str | None = None,
    pitch: str = "+0Hz",
    volume: str = "+0%",
) -> dict[int, TTSResult]:
    """
    按页合成语音，返回 {页码: TTSResult}
    """
    if output_dir is None:
        output_dir = AUDIO_DIR
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    results = {}
    for page_num, text in pages.items():
        out_path = output_dir / f"page_{page_num:03d}.mp3"
        print(f"  合成第 {page_num} 页语音 -> {out_path.name}")
        results[page_num] = synthesize_speech(text, out_path, rate=rate, pitch=pitch, volume=volume)

    return results


# ============ 情感预设 ============
# 通过 rate/pitch/volume 组合模拟不同情感
EMOTION_PRESETS = {
    "default": {"rate": "+0%", "pitch": "+0Hz", "volume": "+0%", "desc": "默认（中性）"},
    "cheerful": {"rate": "+10%", "pitch": "+3Hz", "volume": "+5%", "desc": "开朗活泼"},
    "serious": {"rate": "-5%", "pitch": "-2Hz", "volume": "+0%", "desc": "严肃沉稳"},
    "gentle": {"rate": "-10%", "pitch": "+1Hz", "volume": "-5%", "desc": "温柔舒缓"},
    "energetic": {"rate": "+20%", "pitch": "+5Hz", "volume": "+10%", "desc": "充满活力"},
    "calm": {"rate": "-15%", "pitch": "-1Hz", "volume": "-5%", "desc": "平静从容"},
}


# ============ 可选语音列表 ============
POPULAR_VOICES = {
    # 中文
    "zh-CN-YunxiNeural": "中文男声-云希（年轻阳光）",
    "zh-CN-YunjianNeural": "中文男声-云健（沉稳有力）",
    "zh-CN-XiaoxiaoNeural": "中文女声-晓晓（温柔甜美）",
    "zh-CN-XiaoyiNeural": "中文女声-晓伊（活泼年轻）",
    # 英文
    "en-US-AndrewNeural": "英文男声-Andrew",
    "en-US-AriaNeural": "英文女声-Aria",
}


async def list_voices(language: str = "zh"):
    """列出可用的语音"""
    voices = await edge_tts.list_voices()
    return [v for v in voices if v["Locale"].startswith(language)]


if __name__ == "__main__":
    import sys

    if len(sys.argv) > 1 and sys.argv[1] == "voices":
        print("可用中文语音：")
        voices = asyncio.run(list_voices("zh"))
        for v in voices:
            print(f"  {v['ShortName']}: {v['FriendlyName']}")
    else:
        demo = "大家好，欢迎来到今天的汇报。接下来我将为大家介绍本次项目的核心内容。"
        result = synthesize_speech(demo)
        print(f"合成完成: {result.audio_path} (约 {result.duration_seconds:.1f}s)")
