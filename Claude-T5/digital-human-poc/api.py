"""
数字人汇报系统 - REST API

启动: python api.py
文档: http://localhost:8000/docs
WebSocket: ws://localhost:8000/ws/progress/{job_id}
"""

import asyncio
import json
import os
import sys
import time
import uuid
import threading
from pathlib import Path
from typing import Optional

sys.path.insert(0, str(Path(__file__).parent))

import uvicorn
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, UploadFile, File, Form, HTTPException, BackgroundTasks, Depends, Query
from fastapi.responses import FileResponse, JSONResponse
from fastapi.security import APIKeyHeader
from pydantic import BaseModel

from src.config import OUTPUT_DIR
from src.pipeline import DigitalHumanPipeline
from src.avatar.compositor import RESOLUTION_PRESETS
from src.avatar.merger import TRANSITION_CHOICES
from src.avatar.gallery import scan_avatars, get_avatar_path, AvatarInfo
from src.tts.engine import EMOTION_PRESETS
from src.avatar.virtual_background import BUILTIN_BACKGROUNDS

app = FastAPI(
    title="数字人汇报系统 API",
    description="上传 PPT 生成数字人演讲视频",
    version="1.0.0",
)

# ============ 限流中间件 ============
_RATE_LIMIT = int(os.getenv("RATE_LIMIT", "0"))  # 每分钟最大请求数，0=不限
_rate_store: dict[str, list[float]] = {}
_rate_lock = threading.Lock()


@app.middleware("http")
async def rate_limit_middleware(request, call_next):
    """IP 级别限流中间件"""
    if _RATE_LIMIT <= 0:
        return await call_next(request)
    client = request.client.host if request.client else "unknown"
    now = time.time()
    with _rate_lock:
        if client not in _rate_store:
            _rate_store[client] = []
        # 清理 60s 前的记录
        _rate_store[client] = [t for t in _rate_store[client] if now - t < 60]
        if len(_rate_store[client]) >= _RATE_LIMIT:
            return JSONResponse(
                status_code=429,
                content={"detail": f"请求过于频繁，限制 {_RATE_LIMIT} 次/分钟"},
            )
        _rate_store[client].append(now)
    return await call_next(request)


# ============ API Key 认证 ============
_api_keys_str = os.getenv("API_KEYS", "")
_valid_keys = set(k.strip() for k in _api_keys_str.split(",") if k.strip()) if _api_keys_str else set()
_auth_enabled = bool(_valid_keys)

api_key_header = APIKeyHeader(name="X-API-Key", auto_error=False)


async def verify_api_key(api_key: str = Depends(api_key_header)):
    """验证 API Key（未配置则跳过）"""
    if not _auth_enabled:
        return True
    if api_key not in _valid_keys:
        raise HTTPException(401, "无效的 API Key")
    return True


# 任务存储
_jobs: dict[str, dict] = {}
_job_lock = threading.Lock()
_start_time = time.time()

# WebSocket 进度推送: {job_id: [set of active websockets]}
_ws_clients: dict[str, set] = {}
_ws_lock = threading.Lock()


# ============ 数据模型 ============

class JobStatus(BaseModel):
    job_id: str
    status: str  # pending, running, completed, failed
    progress: float = 0.0
    message: str = ""
    output_path: Optional[str] = None
    error: Optional[str] = None


class TaskListResponse(BaseModel):
    jobs: list[JobStatus]


# ============ 后台任务 ============

def _run_pipeline_job(job_id: str, ppt_path: Path, params: dict):
    """后台运行流水线"""
    with _job_lock:
        _jobs[job_id]["status"] = "running"

    def progress_cb(msg: str, pct: float):
        with _job_lock:
            _jobs[job_id]["progress"] = pct
            _jobs[job_id]["message"] = msg
        # WebSocket 广播
        _ws_broadcast(job_id, {
            "job_id": job_id,
            "status": "running",
            "progress": pct,
            "message": msg,
        })

    try:
        pipeline = DigitalHumanPipeline()
        result = pipeline.run(
            ppt_path=ppt_path,
            progress_callback=progress_cb,
            **params,
        )
        with _job_lock:
            _jobs[job_id]["status"] = "completed"
            _jobs[job_id]["progress"] = 1.0
            _jobs[job_id]["message"] = "完成"
            _jobs[job_id]["output_path"] = str(result)
        _ws_broadcast(job_id, {
            "job_id": job_id,
            "status": "completed",
            "progress": 1.0,
            "message": "完成",
            "output_path": str(result),
        })
    except Exception as e:
        with _job_lock:
            _jobs[job_id]["status"] = "failed"
            _jobs[job_id]["error"] = str(e)
        _ws_broadcast(job_id, {
            "job_id": job_id,
            "status": "failed",
            "error": str(e),
        })


def _ws_broadcast(job_id: str, data: dict):
    """向所有监听该任务的 WebSocket 客户端广播消息"""
    with _ws_lock:
        clients = _ws_clients.get(job_id, set()).copy()
    msg = json.dumps(data, ensure_ascii=False)
    for ws in clients:
        try:
            asyncio.run_coroutine_threadsafe(ws.send_text(msg), asyncio.get_event_loop())
        except Exception:
            pass


# ============ API 端点 ============

@app.get("/")
def root():
    return {"name": "数字人汇报系统 API", "version": "1.0.0", "docs": "/docs"}


@app.get("/health")
def health_check():
    """健康检查"""
    import subprocess
    ffmpeg_ok = subprocess.run(["ffmpeg", "-version"], capture_output=True).returncode == 0
    with _job_lock:
        running = sum(1 for j in _jobs.values() if j["status"] == "running")
    return {
        "status": "ok",
        "uptime_seconds": round(time.time() - _start_time),
        "ffmpeg": "ok" if ffmpeg_ok else "missing",
        "auth_enabled": _auth_enabled,
        "jobs_running": running,
        "avatars": len(scan_avatars()),
    }


@app.get("/avatars")
def list_avatars(_: bool = Depends(verify_api_key)):
    """列出所有可用头像"""
    avatars = scan_avatars()
    return {
        "total": len(avatars),
        "avatars": [a.__dict__ for a in avatars],
    }


@app.get("/avatars/{avatar_id}/image")
def get_avatar_image(avatar_id: str, _: bool = Depends(verify_api_key)):
    """获取头像图片"""
    path = Path(get_avatar_path(avatar_id))
    if not path.exists():
        raise HTTPException(404, f"头像不存在: {avatar_id}")
    return FileResponse(path, media_type="image/png", filename=path.name)


@app.get("/options")
def get_options(_: bool = Depends(verify_api_key)):
    """获取所有可选项"""
    avatars = scan_avatars()
    return {
        "styles": ["formal", "casual", "training"],
        "emotions": {k: v["desc"] for k, v in EMOTION_PRESETS.items()},
        "transitions": {k: v for k, v in TRANSITION_CHOICES.items()},
        "resolutions": {k: f"{w}x{h}" for k, (w, h) in RESOLUTION_PRESETS.items()},
        "layouts": ["pip", "avatar-only"],
        "languages": ["auto", "zh", "en"],
        "avatars": [{"id": a.id, "name": a.name, "gender": a.gender,
                      "style": a.style, "builtin": a.builtin} for a in avatars],
        "virtual_backgrounds": list(BUILTIN_BACKGROUNDS.keys()),
    }


@app.post("/generate", response_model=JobStatus)
async def generate(
    background_tasks: BackgroundTasks,
    ppt: UploadFile = File(..., description="PPT 文件"),
    style: str = Form("formal"),
    language: str = Form("auto"),
    emotion: str = Form("default"),
    transition: str = Form("fade"),
    resolution: str = Form("720p"),
    layout: str = Form("pip"),
    subtitles: bool = Form(True),
    bgm: bool = Form(True),
    rate: str = Form("+0%"),
    use_fallback: bool = Form(True),
    parallel: bool = Form(False),
    watermark_text: Optional[str] = Form(None),
    avatar_id: Optional[str] = Form(None, description="头像 ID（从 /options 获取，空=默认头像）"),
    virtual_background: Optional[str] = Form(None, description="虚拟背景: 图片路径或预设名"),
    trim_start: float = Form(0.0, description="裁剪: 跳过前 N 秒"),
    trim_end: Optional[float] = Form(None, description="裁剪: 在第 N 秒截断"),
    auth: bool = Depends(verify_api_key),
):
    """提交生成任务（异步）"""
    # 保存上传的 PPT
    job_id = uuid.uuid4().hex[:12]
    job_dir = OUTPUT_DIR / f"api_{job_id}"
    job_dir.mkdir(parents=True, exist_ok=True)

    ppt_path = job_dir / ppt.filename
    content = await ppt.read()
    ppt_path.write_bytes(content)

    # 注册任务
    with _job_lock:
        _jobs[job_id] = {
            "job_id": job_id,
            "status": "pending",
            "progress": 0.0,
            "message": "排队中",
            "output_path": None,
            "error": None,
        }

    params = {
        "style": style,
        "language": language,
        "emotion": emotion,
        "transition": transition,
        "resolution": resolution,
        "layout": layout,
        "subtitles": subtitles,
        "bgm": bgm,
        "rate": rate,
        "use_fallback": use_fallback,
        "parallel": parallel,
        "watermark_text": watermark_text,
        "generate_cover_image": True,
        "use_cache": True,
        "avatar_image": get_avatar_path(avatar_id),
        "virtual_background": virtual_background,
        "trim_start": trim_start,
        "trim_end": trim_end,
    }

    # 后台执行
    background_tasks.add_task(_run_pipeline_job, job_id, ppt_path, params)

    return JobStatus(**_jobs[job_id])


@app.get("/jobs/{job_id}", response_model=JobStatus)
def get_job_status(job_id: str, _: bool = Depends(verify_api_key)):
    """查询任务状态"""
    with _job_lock:
        if job_id not in _jobs:
            raise HTTPException(404, f"任务不存在: {job_id}")
        return JobStatus(**_jobs[job_id])


@app.get("/jobs", response_model=TaskListResponse)
def list_jobs(_: bool = Depends(verify_api_key)):
    """列出所有任务"""
    with _job_lock:
        return TaskListResponse(jobs=[JobStatus(**j) for j in _jobs.values()])


@app.get("/download/{job_id}")
def download_result(job_id: str, _: bool = Depends(verify_api_key)):
    """下载生成结果"""
    with _job_lock:
        if job_id not in _jobs:
            raise HTTPException(404, f"任务不存在: {job_id}")
        job = _jobs[job_id]

    if job["status"] != "completed" or not job["output_path"]:
        raise HTTPException(400, "任务未完成或无输出文件")

    path = Path(job["output_path"])
    if not path.exists():
        raise HTTPException(404, "输出文件不存在")

    return FileResponse(
        path,
        media_type="video/mp4",
        filename=path.name,
    )


@app.delete("/jobs/{job_id}")
def delete_job(job_id: str, _: bool = Depends(verify_api_key)):
    """删除任务记录"""
    with _job_lock:
        if job_id not in _jobs:
            raise HTTPException(404, f"任务不存在: {job_id}")
        del _jobs[job_id]
    return {"ok": True}


# ============ WebSocket 实时进度 ============

@app.websocket("/ws/progress/{job_id}")
async def ws_progress(websocket: WebSocket, job_id: str):
    """
    WebSocket 实时进度推送

    连接: ws://localhost:8000/ws/progress/{job_id}

    服务端推送消息格式:
    {
        "job_id": "xxx",
        "status": "running" | "completed" | "failed",
        "progress": 0.0~1.0,
        "message": "当前步骤描述",
        "output_path": "..." | null,   // 仅 completed
        "error": "..." | null          // 仅 failed
    }
    """
    await websocket.accept()

    # 检查任务是否存在
    with _job_lock:
        if job_id not in _jobs:
            await websocket.send_text(json.dumps({"error": f"任务不存在: {job_id}"}))
            await websocket.close()
            return

    # 注册客户端
    with _ws_lock:
        if job_id not in _ws_clients:
            _ws_clients[job_id] = set()
        _ws_clients[job_id].add(websocket)

    try:
        # 先发送当前状态
        with _job_lock:
            current = _jobs.get(job_id, {})
        await websocket.send_text(json.dumps({
            "job_id": job_id,
            "status": current.get("status", "pending"),
            "progress": current.get("progress", 0.0),
            "message": current.get("message", ""),
            "output_path": current.get("output_path"),
            "error": current.get("error"),
        }, ensure_ascii=False))

        # 保持连接，等待任务完成
        while True:
            with _job_lock:
                job = _jobs.get(job_id, {})
                status = job.get("status", "pending")
            if status in ("completed", "failed"):
                break
            await asyncio.sleep(0.5)

    except WebSocketDisconnect:
        pass
    finally:
        with _ws_lock:
            if job_id in _ws_clients:
                _ws_clients[job_id].discard(websocket)
                if not _ws_clients[job_id]:
                    del _ws_clients[job_id]


if __name__ == "__main__":
    print("\n" + "=" * 50)
    print("  数字人汇报系统 REST API")
    print("  文档: http://localhost:8000/docs")
    print("=" * 50 + "\n")
    uvicorn.run(app, host="0.0.0.0", port=8000)
