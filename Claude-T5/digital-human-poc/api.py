"""
数字人汇报系统 - REST API

启动: python api.py
文档: http://localhost:8000/docs
"""

import sys
import uuid
import threading
from pathlib import Path
from typing import Optional

sys.path.insert(0, str(Path(__file__).parent))

import uvicorn
from fastapi import FastAPI, UploadFile, File, Form, HTTPException, BackgroundTasks
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel

from src.config import OUTPUT_DIR
from src.pipeline import DigitalHumanPipeline
from src.avatar.compositor import RESOLUTION_PRESETS
from src.avatar.merger import TRANSITION_CHOICES
from src.tts.engine import EMOTION_PRESETS

app = FastAPI(
    title="数字人汇报系统 API",
    description="上传 PPT 生成数字人演讲视频",
    version="1.0.0",
)

# 任务存储
_jobs: dict[str, dict] = {}
_job_lock = threading.Lock()


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
    except Exception as e:
        with _job_lock:
            _jobs[job_id]["status"] = "failed"
            _jobs[job_id]["error"] = str(e)


# ============ API 端点 ============

@app.get("/")
def root():
    return {"name": "数字人汇报系统 API", "version": "1.0.0", "docs": "/docs"}


@app.get("/options")
def get_options():
    """获取所有可选项"""
    return {
        "styles": ["formal", "casual", "training"],
        "emotions": {k: v["desc"] for k, v in EMOTION_PRESETS.items()},
        "transitions": {k: v for k, v in TRANSITION_CHOICES.items()},
        "resolutions": {k: f"{w}x{h}" for k, (w, h) in RESOLUTION_PRESETS.items()},
        "layouts": ["pip", "avatar-only"],
        "languages": ["auto", "zh", "en"],
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
    }

    # 后台执行
    background_tasks.add_task(_run_pipeline_job, job_id, ppt_path, params)

    return JobStatus(**_jobs[job_id])


@app.get("/jobs/{job_id}", response_model=JobStatus)
def get_job_status(job_id: str):
    """查询任务状态"""
    with _job_lock:
        if job_id not in _jobs:
            raise HTTPException(404, f"任务不存在: {job_id}")
        return JobStatus(**_jobs[job_id])


@app.get("/jobs", response_model=TaskListResponse)
def list_jobs():
    """列出所有任务"""
    with _job_lock:
        return TaskListResponse(jobs=[JobStatus(**j) for j in _jobs.values()])


@app.get("/download/{job_id}")
def download_result(job_id: str):
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
def delete_job(job_id: str):
    """删除任务记录"""
    with _job_lock:
        if job_id not in _jobs:
            raise HTTPException(404, f"任务不存在: {job_id}")
        del _jobs[job_id]
    return {"ok": True}


if __name__ == "__main__":
    print("\n" + "=" * 50)
    print("  数字人汇报系统 REST API")
    print("  文档: http://localhost:8000/docs")
    print("=" * 50 + "\n")
    uvicorn.run(app, host="0.0.0.0", port=8000)
