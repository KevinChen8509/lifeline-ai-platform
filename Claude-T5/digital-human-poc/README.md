# 数字人汇报系统 (Digital Human Presenter)

上传 PPT 文件，自动生成演讲稿、语音合成、数字人视频，输出完整的画中画演示视频。

## 功能特性

| 功能 | 说明 |
|------|------|
| PPT 解析 | 提取标题、文本、表格、备注，导出幻灯片图片 |
| 演讲稿生成 | LLM 自动生成逐页演讲稿，支持正式/轻松/培训三种风格 |
| 语音合成 | Edge TTS 免费 TTS，6 种中英文语音，支持语速调节 |
| 数字人驱动 | SadTalker 面部动画 (GPU/CPU) 或 FFmpeg 静态图片模式 |
| 画中画合成 | PPT 全屏 + 数字人右下角叠加 |
| 字幕叠加 | 自动分句生成 SRT，FFmpeg 硬字幕烧录 |
| 转场效果 | 页面间淡入淡出过渡 |
| 背景音乐 | 内置 BGM 或上传自定义音乐，自动混音 |
| 批量处理 | 同时上传多个 PPT，队列式生成 |
| 历史记录 | 自动记录生成历史，可回看 |
| Web UI | Gradio 浏览器界面，实时进度显示 |
| 素材导出 | 演讲稿、单页音频、SRT 字幕、视频一键下载 |

## 快速开始

### 1. 环境要求

- Python 3.10+
- FFmpeg（视频处理）
- PowerPoint 2016+（PPT 幻灯片导出，仅 Windows）

### 2. 安装依赖

```bash
cd digital-human-poc

# 推荐: 使用 uv
uv pip install -r requirements.txt

# 或使用 pip
pip install -r requirements.txt

# Windows PPT 导出需要 pywin32
pip install pywin32
```

### 3. 安装 FFmpeg

```bash
# Windows
winget install FFmpeg

# 验证
ffmpeg -version
```

### 4. 配置 API

```bash
cp .env.example .env
# 编辑 .env，填入 LLM API Key
```

支持所有 OpenAI 兼容格式的 API（OpenAI、DeepSeek、通义千问、火山引擎等）。

### 5. 运行

```bash
# 启动 Web UI（推荐）
python web.py
# 访问 http://localhost:7860

# 或使用 CLI
python demo.py --fallback
```

## Web UI 使用

```bash
python web.py
```

访问 http://localhost:7860，左侧配置，右侧查看结果：

- **上传 PPT** — 支持 .pptx 文件，可多选批量处理
- **演讲风格** — 正式汇报 / 轻松讲解 / 培训教学
- **语音** — 6 种中英文语音
- **语速** — 慢速 -20% / 正常 / 快速 +20% / 极快 +50%
- **头像** — 默认头像或上传自定义人脸照片
- **驱动模式** — 快速模式 (静态图片) 或 SadTalker (面部动画)
- **视频布局** — 画中画 (PPT+数字人) / 仅数字人
- **叠加字幕** — 自动生成字幕并烧录到视频
- **背景音乐** — 内置 BGM 或上传自定义音乐

三个操作按钮：
- **生成** — 完整流水线，包含所有功能
- **快速预览** — 每页截取前 50 字，跳过 PIP/字幕/BGM，快速看效果
- **批量生成** — 逐个处理所有上传的 PPT

## CLI 使用

```bash
# 基本用法（快速模式）
python demo.py --fallback

# 指定 PPT
python demo.py --ppt path/to/file.pptx --fallback

# 完整模式（需要 SadTalker）
python demo.py --ppt samples/demo.pptx

# 仅生成音频
python demo.py --fallback --skip-avatar

# 调节语速
python demo.py --fallback --rate +20%

# 自定义头像
python demo.py --fallback --avatar my_photo.png

# 自定义 BGM
python demo.py --fallback --bgm-file music.mp3

# 批量处理目录下所有 PPT
python demo.py --batch ./presentations/ --fallback

# 禁用字幕
python demo.py --fallback --no-subtitles

# 禁用 BGM
python demo.py --fallback --no-bgm

# 仅数字人，不含 PPT 画面
python demo.py --fallback --layout avatar-only
```

### 全部参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--ppt` | `samples/demo.pptx` | PPT 文件路径 |
| `--style` | `formal` | 演讲风格 (formal/casual/training) |
| `--fallback` | - | 使用静态图片模式（快速，无面部动画） |
| `--skip-avatar` | - | 仅生成音频，跳过视频 |
| `--avatar` | 默认头像 | 头像图片路径 |
| `--layout` | `pip` | 视频布局 (pip/avatar-only) |
| `--rate` | `+0%` | 语速 (如 +20%, -10%) |
| `--bgm-file` | 内置 BGM | 自定义背景音乐文件 |
| `--no-subtitles` | - | 禁用字幕叠加 |
| `--no-bgm` | - | 禁用背景音乐 |
| `--batch` | - | 批量处理：指定目录处理所有 .pptx |

## 项目结构

```
digital-human-poc/
├── web.py                      # Gradio Web UI
├── demo.py                     # CLI 快速演示入口
├── src/
│   ├── config.py               # 全局配置
│   ├── pipeline.py             # 主流水线（串联所有模块）
│   ├── history.py              # 生成历史记录
│   ├── parsers/
│   │   ├── ppt_parser.py       # PPT 解析：文本/表格/备注
│   │   └── slide_renderer.py   # PPT 幻灯片导出为 PNG (COM)
│   ├── generators/
│   │   └── script_generator.py # LLM 演讲稿生成
│   ├── tts/
│   │   └── engine.py           # Edge TTS 语音合成
│   └── avatar/
│       ├── sadtalker_driver.py # SadTalker 面部动画驱动
│       ├── fallback_driver.py  # FFmpeg 静态图片降级方案
│       ├── compositor.py       # 画中画合成
│       ├── subtitle.py         # SRT 字幕生成与烧录
│       ├── bgm.py              # 背景音乐混音
│       └── merger.py           # 视频合并（含转场效果）
├── assets/
│   └── bgm_default.wav         # 默认背景音乐
├── samples/
│   └── demo.pptx               # 示例 PPT
├── templates/
│   └── default_avatar.png      # 默认头像
├── .env.example                # 环境变量模板
└── requirements.txt            # Python 依赖
```

## 处理流水线

```
PPT 文件
  │
  ├─[1] 解析 PPT ─→ 提取文本/表格/备注
  │
  ├─[2] 导出幻灯片图片 ─→ PowerPoint COM → PNG
  │
  ├─[3] LLM 生成演讲稿 ─→ 逐页演讲文本
  │
  ├─[4] TTS 语音合成 ─→ 逐页 MP3 音频
  │
  ├─[5] 数字人视频 ─→ SadTalker 或 FFmpeg 静态图片
  │     │
  │     ├─ 画中画合成 (PIP)
  │     ├─ 字幕叠加 (SRT)
  │     ├─ 转场合并 (xfade)
  │     └─ 背景音乐混音 (amix)
  │
  └─→ 最终视频 (.mp4)
```

## 技术栈

| 模块 | 技术 |
|------|------|
| PPT 解析 | python-pptx |
| 幻灯片导出 | pywin32 (PowerPoint COM) |
| 演讲稿 | OpenAI 兼容 API (火山引擎/DeepSeek/GPT) |
| 语音合成 | Edge TTS (免费) |
| 面部动画 | SadTalker (PyTorch) |
| 视频处理 | FFmpeg 8.1 |
| Web UI | Gradio |
| 语言 | Python 3.12 |

## 环境变量

```bash
# .env 配置
LLM_API_KEY=your-api-key
LLM_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
LLLM_MODEL=your-model-endpoint
LLM_TEMPERATURE=0.7

TTS_ENGINE=edge-tts
TTS_VOICE=zh-CN-YunxiNeural    # 语音角色
TTS_RATE=+0%                    # 默认语速

AVATAR_ENGINE=sadtalker
AVATAR_SOURCE_IMAGE=SadTalker/examples/source_image/art_0.png
```

### 可用语音

| 语音 ID | 描述 |
|---------|------|
| `zh-CN-YunxiNeural` | 中文男声-云希（年轻阳光） |
| `zh-CN-YunjianNeural` | 中文男声-云健（沉稳有力） |
| `zh-CN-XiaoxiaoNeural` | 中文女声-晓晓（温柔甜美） |
| `zh-CN-XiaoyiNeural` | 中文女声-晓伊（活泼年轻） |
| `en-US-AndrewNeural` | 英文男声-Andrew |
| `en-US-AriaNeural` | 英文女声-Aria |

## SadTalker 安装（可选）

安装 SadTalker 后可获得真实面部动画效果，否则自动降级为静态图片模式。

```bash
# 需要 PyTorch + CUDA 环境
pip install torch torchvision
# SadTalker 源码安装见: https://github.com/OpenTalker/SadTalker
```

GPU 显存要求: >= 3GB。不足时自动使用 CPU 模式（较慢，约 32s/帧）。

## 许可证

MIT
