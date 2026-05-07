"""数字人汇报系统 - 全局配置"""

import os
from pathlib import Path
from dotenv import load_dotenv

load_dotenv()

# ============ 路径 ============
BASE_DIR = Path(__file__).resolve().parent.parent
OUTPUT_DIR = BASE_DIR / "outputs"
AUDIO_DIR = BASE_DIR / "audio"
VIDEO_DIR = BASE_DIR / "video"
TEMPLATES_DIR = BASE_DIR / "templates"

for d in [OUTPUT_DIR, AUDIO_DIR, VIDEO_DIR, TEMPLATES_DIR]:
    d.mkdir(parents=True, exist_ok=True)

# ============ LLM 配置 ============
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "https://api.openai.com/v1")
LLM_MODEL = os.getenv("LLM_MODEL", "gpt-4o")
LLM_TEMPERATURE = float(os.getenv("LLM_TEMPERATURE", "0.7"))

# ============ TTS 配置 ============
TTS_ENGINE = os.getenv("TTS_ENGINE", "edge-tts")  # edge-tts | cosyvoice
TTS_VOICE = os.getenv("TTS_VOICE", "zh-CN-YunxiNeural")  # Edge TTS 中文男声
TTS_RATE = os.getenv("TTS_RATE", "+0%")  # 语速调整

# ============ 数字人配置 ============
AVATAR_ENGINE = os.getenv("AVATAR_ENGINE", "sadtalker")  # sadtalker | musetalk
AVATAR_SOURCE_IMAGE = os.getenv("AVATAR_SOURCE_IMAGE", str(TEMPLATES_DIR / "default_avatar.png"))
AVATAR_PREPROCESSOR = os.getenv("AVATAR_PREPROCESSOR", "crop")  # crop | resize | full
AVATAR_STILL = os.getenv("AVATAR_STILL", "true")

# ============ 输出配置 ============
OUTPUT_FORMAT = os.getenv("OUTPUT_FORMAT", "mp4")
OUTPUT_RESOLUTION = os.getenv("OUTPUT_RESOLUTION", "720p")
