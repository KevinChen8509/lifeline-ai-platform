# 数字人汇报系统 (Digital Human Presenter)

上传 PPT 文件，自动生成演讲稿、语音合成、数字人视频，输出完整的画中画演示视频。

## 功能特性

| 功能 | 说明 |
|------|------|
| PPT 解析 | 提取标题、文本、表格、备注，导出幻灯片图片 |
| 演讲稿生成 | LLM 自动生成逐页演讲稿，支持正式/轻松/培训三种风格 |
| 多语言支持 | 自动检测中英文，中英双语演讲稿和语音 |
| 语音合成 | Edge TTS 免费 TTS，6 种中英文语音，6 种情感风格 |
| 数字人驱动 | SadTalker 面部动画 (GPU/CPU) 或 FFmpeg 静态图片模式 |
| 画中画合成 | PPT 全屏 + 数字人右下角叠加 |
| 字幕叠加 | 自动分句生成 SRT，FFmpeg 硬字幕烧录 |
| 转场效果 | 8 种转场: 淡入淡出、擦除、溶解、滑入、圆形裁剪等 |
| 水印 | 文字水印或图片 Logo，可调位置/透明度 |
| 封面生成 | 自动从 PPT 首张幻灯片或视频帧提取封面 |
| 背景音乐 | 内置 BGM 或上传自定义音乐，自动混音 |
| 视频分辨率 | 480p / 720p / 1080p / 4K 可选 |
| 并行处理 | TTS 和视频并行生成，显著加速 |
| TTS 缓存 | 相同文本+语音+参数自动跳过，节省重复合成时间 |
| 断点续传 | 生成失败后从上次进度继续 |
| 批量处理 | 同时上传多个 PPT，队列式生成 |
| 历史记录 | 自动记录生成历史，可回看 |
| Web UI | Gradio 浏览器界面，实时流式进度显示 |
| REST API | FastAPI 异步接口，支持外部集成 |
| API 认证 | X-API-Key 保护，健康检查端点 |
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

# 启动 REST API
python api.py
# 文档 http://localhost:8000/docs

# 或使用 CLI
python demo.py --fallback
```

## Web UI 使用

```bash
python web.py
```

访问 http://localhost:7860，左侧配置，右侧查看结果：

**基础配置:**
- **上传 PPT** — 支持 .pptx 文件，可多选批量处理
- **演讲风格** — 正式汇报 / 轻松讲解 / 培训教学
- **演讲语言** — 自动检测 / 中文 / English
- **语音情感** — 默认、开朗、严肃、温柔、活力、平静
- **语音** — 6 种中英文语音
- **语速** — 慢速 -20% / 正常 / 快速 +20% / 极快 +50%
- **头像** — 默认头像或上传自定义人脸照片

**视频配置:**
- **驱动模式** — 快速模式 (静态图片) 或 SadTalker (面部动画)
- **视频布局** — 画中画 (PPT+数字人) / 仅数字人
- **转场效果** — 8 种转场可选
- **输出分辨率** — 480p / 720p / 1080p / 4K
- **叠加字幕** — 自动生成字幕并烧录到视频

**增强选项:**
- **文字水印** — 输入水印文字
- **图片水印** — 上传 Logo
- **背景音乐** — 内置 BGM 或上传自定义音乐
- **生成封面图** — 自动提取封面
- **并行处理** — 加速 TTS + 视频生成
- **TTS 缓存** — 相同文本跳过重复合成

三个操作按钮：
- **生成** — 完整流水线，包含所有功能
- **快速预览** — 每页截取前 50 字，跳过 PIP/字幕/BGM，快速看效果
- **批量生成** — 逐个处理所有上传的 PPT

## REST API

```bash
# 启动 API 服务
python api.py
# 文档: http://localhost:8000/docs
```

**端点列表:**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | API 信息 |
| GET | `/health` | 健康检查 |
| GET | `/options` | 获取所有可选项 |
| POST | `/generate` | 提交生成任务（异步） |
| GET | `/jobs` | 列出所有任务 |
| GET | `/jobs/{id}` | 查询任务状态 |
| GET | `/download/{id}` | 下载生成结果 |
| DELETE | `/jobs/{id}` | 删除任务 |

**认证:** 在 `.env` 中设置 `API_KEYS=key1,key2` 启用 API Key 认证。请求头添加 `X-API-Key: key1`。

**示例:**

```bash
# 提交任务
curl -X POST http://localhost:8000/generate \
  -F "ppt=@demo.pptx" \
  -F "style=formal" \
  -F "resolution=1080p" \
  -F "transition=fade"

# 查询状态
curl http://localhost:8000/jobs/{job_id}

# 下载结果
curl -O http://localhost:8000/download/{job_id}
```

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

# 调节语速和情感
python demo.py --fallback --rate +20% --emotion cheerful

# 自定义头像
python demo.py --fallback --avatar my_photo.png

# 自定义 BGM
python demo.py --fallback --bgm-file music.mp3

# 高分辨率 + 转场 + 水印
python demo.py --fallback --resolution 1080p --transition dissolve --watermark "公司名"

# 并行处理 + 缓存
python demo.py --fallback --parallel

# 断点续传（失败后继续）
python demo.py --fallback --resume

# 批量处理目录下所有 PPT
python demo.py --batch ./presentations/ --fallback
```

### 全部参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--ppt` | `samples/demo.pptx` | PPT 文件路径 |
| `--style` | `formal` | 演讲风格 (formal/casual/training) |
| `--fallback` | - | 使用静态图片模式（快速） |
| `--skip-avatar` | - | 仅生成音频，跳过视频 |
| `--avatar` | 默认头像 | 头像图片路径 |
| `--layout` | `pip` | 视频布局 (pip/avatar-only) |
| `--rate` | `+0%` | 语速 (如 +20%, -10%) |
| `--language` | `auto` | 演讲语言 (auto/zh/en) |
| `--emotion` | `default` | 情感风格 (default/cheerful/serious/gentle/energetic/calm) |
| `--transition` | `fade` | 转场效果 (fade/wipeleft/wipeup/dissolve/slidedown/circlecrop/radial/smoothleft/none) |
| `--watermark` | - | 文字水印内容 |
| `--watermark-image` | - | 图片水印路径 |
| `--resolution` | `720p` | 输出分辨率 (480p/720p/1080p/4K) |
| `--parallel` | - | 并行处理 |
| `--no-cache` | - | 禁用 TTS 缓存 |
| `--no-cover` | - | 不生成封面图 |
| `--bgm-file` | 内置 BGM | 自定义背景音乐文件 |
| `--no-subtitles` | - | 禁用字幕叠加 |
| `--no-bgm` | - | 禁用背景音乐 |
| `--batch` | - | 批量处理：指定目录处理所有 .pptx |

## 项目结构

```
digital-human-poc/
├── web.py                      # Gradio Web UI
├── api.py                      # FastAPI REST API
├── demo.py                     # CLI 快速演示入口
├── src/
│   ├── config.py               # 全局配置
│   ├── pipeline.py             # 主流水线（串联所有模块，含断点续传）
│   ├── history.py              # 生成历史记录
│   ├── parsers/
│   │   ├── ppt_parser.py       # PPT 解析：文本/表格/备注
│   │   └── slide_renderer.py   # PPT 幻灯片导出为 PNG (COM)
│   ├── generators/
│   │   └── script_generator.py # LLM 演讲稿生成（中英双语）
│   ├── detectors/
│   │   └── language.py         # 语言自动检测
│   ├── tts/
│   │   └── engine.py           # Edge TTS 语音合成（含缓存+并行）
│   └── avatar/
│       ├── sadtalker_driver.py # SadTalker 面部动画驱动
│       ├── fallback_driver.py  # FFmpeg 静态图片降级方案（含并行）
│       ├── compositor.py       # 画中画合成（多分辨率）
│       ├── subtitle.py         # SRT 字幕生成与烧录
│       ├── bgm.py              # 背景音乐混音
│       ├── merger.py           # 视频合并（8 种转场效果）
│       ├── watermark.py        # 文字/图片水印
│       └── cover.py            # 视频封面生成
├── tests/                      # 42 个单元测试
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
  │       ↓ 检查点: slides
  ├─[2] 导出幻灯片图片 ─→ PowerPoint COM → PNG (按分辨率)
  │       ↓ 检查点: slides
  ├─[3] LLM 生成演讲稿 ─→ 逐页演讲文本 (中英双语)
  │       ↓ 检查点: script
  ├─[4] TTS 语音合成 ─→ 逐页 MP3 音频 (缓存+并行)
  │       ↓ 检查点: tts
  ├─[5] 数字人视频 ─→ SadTalker 或 FFmpeg 静态图片 (并行)
  │       ↓ 检查点: video
  │     ├─ 画中画合成 (PIP, 多分辨率)
  │     ├─ 字幕叠加 (SRT)
  │     ├─ 转场合并 (8 种 xfade)
  │     ├─ 背景音乐混音 (amix)
  │     ├─ 水印叠加 (drawtext/overlay)
  │     └─ 封面生成
  │
  └─→ 最终视频 (.mp4) + 封面 (.png)
```

## 技术栈

| 模块 | 技术 |
|------|------|
| PPT 解析 | python-pptx |
| 幻灯片导出 | pywin32 (PowerPoint COM) |
| 演讲稿 | OpenAI 兼容 API (火山引擎/DeepSeek/GPT) |
| 语言检测 | 中文字符/英文单词比例 |
| 语音合成 | Edge TTS (免费，缓存+并行) |
| 情感控制 | rate/pitch/volume 组合预设 |
| 面部动画 | SadTalker (PyTorch) |
| 视频处理 | FFmpeg 8.1 (xfade/overlay/drawtext/amix) |
| Web UI | Gradio |
| REST API | FastAPI + Uvicorn |
| 语言 | Python 3.12 |

## 环境变量

```bash
# .env 配置
LLM_API_KEY=your-api-key
LLM_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
LLM_MODEL=your-model-endpoint
LLM_TEMPERATURE=0.7

TTS_ENGINE=edge-tts
TTS_VOICE=zh-CN-YunxiNeural    # 语音角色
TTS_RATE=+0%                    # 默认语速

AVATAR_ENGINE=sadtalker
AVATAR_SOURCE_IMAGE=SadTalker/examples/source_image/art_0.png

# API 认证（逗号分隔多个 Key）
API_KEYS=key1,key2
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

### 情感风格

| 风格 | 语速 | 音调 | 音量 | 描述 |
|------|------|------|------|------|
| `default` | +0% | +0Hz | +0% | 中性 |
| `cheerful` | +10% | +3Hz | +5% | 开朗活泼 |
| `serious` | -5% | -2Hz | +0% | 严肃沉稳 |
| `gentle` | -10% | +1Hz | -5% | 温柔舒缓 |
| `energetic` | +20% | +5Hz | +10% | 充满活力 |
| `calm` | -15% | -1Hz | -5% | 平静从容 |

### 转场效果

| 名称 | 效果 |
|------|------|
| `fade` | 淡入淡出 |
| `wipeleft` | 从右向左擦除 |
| `wipeup` | 从下向上擦除 |
| `dissolve` | 溶解 |
| `slidedown` | 向下滑入 |
| `circlecrop` | 圆形裁剪 |
| `radial` | 径向扩散 |
| `smoothleft` | 平滑左移 |

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
