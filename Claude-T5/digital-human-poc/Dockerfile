# 数字人汇报系统 - Docker 镜像
# 快速模式 (CPU only, 无 SadTalker)
#
# 构建并启动:
#   docker compose up --build
#
# 访问:
#   Web UI: http://localhost:7860
#   API:    http://localhost:8000/docs

FROM python:3.12-slim AS base

# 系统依赖: FFmpeg + 字体
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Python 依赖（利用缓存层）
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 应用代码
COPY src/ src/
COPY assets/ assets/
COPY templates/ templates/
COPY web.py api.py demo.py run.py ./
COPY samples/ samples/

# 输出目录
RUN mkdir -p outputs audio video

# 环境变量（可被 docker-compose 覆盖）
ENV PYTHONUNBUFFERED=1
ENV AVATAR_ENGINE=fallback

# 默认启动 Web UI
EXPOSE 7860 8000

CMD ["python", "-u", "web.py"]


# ---- GPU 变体 (含 SadTalker) ----
# 取消注释以下内容并 docker build --target gpu
FROM base AS gpu

# CUDA 依赖
RUN pip install --no-cache-dir torch torchvision --index-url https://download.pytorch.org/whl/cu121
RUN pip install --no-cache-dir \
    facexlib>=0.3.0 \
    gfpgan>=1.3.8 \
    dlib-bin \
    opencv-python-headless \
    librosa \
    soundfile

# SadTalker 源码（需预先 clone 到项目根目录）
COPY SadTalker/ SadTalker/

ENV AVATAR_ENGINE=sadtalker
