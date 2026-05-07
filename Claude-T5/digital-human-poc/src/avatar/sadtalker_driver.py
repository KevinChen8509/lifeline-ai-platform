"""数字人驱动 - SadTalker 面部驱动（源码调用方式）"""

import subprocess
import sys
import time
from pathlib import Path

from src.config import (
    AVATAR_SOURCE_IMAGE,
    AVATAR_PREPROCESSOR,
    AVATAR_STILL,
    VIDEO_DIR,
)

# SadTalker 源码路径
SADTALKER_DIR = Path(__file__).resolve().parent.parent.parent / "SadTalker"

# SadTalker GPU 推理所需的最小显存 (bytes)
MIN_GPU_VRAM_BYTES = 3 * 1024 * 1024 * 1024  # 3 GB


def _detect_gpu_vram() -> int | None:
    """检测 GPU 显存大小，返回字节数或 None"""
    try:
        import torch
        if not torch.cuda.is_available():
            return None
        return torch.cuda.get_device_properties(0).total_mem
    except Exception:
        return None


def should_use_cpu() -> bool:
    """判断是否应使用 CPU 模式（GPU 不可用或显存不足时返回 True）"""
    vram = _detect_gpu_vram()
    if vram is None:
        return True
    return vram < MIN_GPU_VRAM_BYTES


def check_sadtalker_installed() -> bool:
    """检查 SadTalker 源码和模型是否存在"""
    has_source = (SADTALKER_DIR / "inference.py").exists()
    checkpoints = SADTALKER_DIR / "checkpoints"
    has_old_model = (checkpoints / "epoch_20.pth").exists()
    has_new_model = any(checkpoints.glob("*.safetensors"))
    return has_source and (has_old_model or has_new_model)


def generate_video_sadtalker(
    audio_path: str | Path,
    source_image: str | Path | None = None,
    output_path: str | Path | None = None,
    still: bool = True,
    preprocess: str = "crop",
    enhancer: str | None = None,
    size: int = 256,
    use_cpu: bool | None = None,
) -> Path:
    """
    使用 SadTalker 从音频 + 头像图片生成数字人视频

    Args:
        audio_path: TTS 生成的音频文件
        source_image: 头像图片路径
        output_path: 输出视频路径
        still: 是否使用静态模式（减少头部运动）
        preprocess: 预处理方式 (crop|resize|full)
        enhancer: 面部增强器 (gfpgan|RestoreFormer|None)
        size: 输出分辨率 (256|512)
        use_cpu: 是否使用 CPU 模式 (None=自动检测)
    """
    audio_path = Path(audio_path)
    source_image = Path(source_image or AVATAR_SOURCE_IMAGE)
    output_path = Path(output_path or VIDEO_DIR / f"{audio_path.stem}.mp4")

    if not source_image.exists():
        raise FileNotFoundError(
            f"头像图片不存在: {source_image}\n"
            "请将一张正面人脸照片放到 templates/ 目录下"
        )

    output_path.parent.mkdir(parents=True, exist_ok=True)

    # 自动检测 GPU/CPU
    if use_cpu is None:
        use_cpu = should_use_cpu()

    result_dir = str(output_path.parent)

    # 记录 result_dir 中已有的 mp4 文件，用于后续识别新生成的文件
    existing_mp4s = set(Path(result_dir).glob("*.mp4"))

    cmd = [
        sys.executable,
        str(SADTALKER_DIR / "inference.py"),
        "--driven_audio", str(audio_path),
        "--source_image", str(source_image),
        "--result_dir", result_dir,
        "--preprocess", preprocess,
        "--size", str(size),
        "--checkpoint_dir", str(SADTALKER_DIR / "checkpoints"),
    ]
    if still:
        cmd.append("--still")
    if use_cpu:
        cmd.append("--cpu")
    if enhancer:
        cmd.extend(["--enhancer", enhancer])

    mode = "CPU" if use_cpu else "GPU"
    print(f"  SadTalker [{mode}]: {audio_path.name} + {source_image.name}")

    t0 = time.time()
    result = subprocess.run(cmd, text=True, cwd=str(SADTALKER_DIR))
    elapsed = time.time() - t0

    if result.returncode != 0:
        raise RuntimeError(f"SadTalker 执行失败 (exit code {result.returncode})")

    # SadTalker 将输出移动到 result_dir/timestamp.mp4
    # 找到新生成的 mp4 文件（不在 existing_mp4s 中的）
    current_mp4s = set(Path(result_dir).glob("*.mp4"))
    new_files = current_mp4s - existing_mp4s

    if new_files:
        # 取最新的文件
        latest = max(new_files, key=lambda f: f.stat().st_mtime)
        if latest != output_path:
            # 如果目标文件已存在，先删除
            if output_path.exists():
                output_path.unlink()
            latest.rename(output_path)
    elif not output_path.exists():
        raise RuntimeError(f"SadTalker 未生成视频文件，检查输出目录: {result_dir}")

    print(f"    完成 ({elapsed:.0f}s) -> {output_path.name}")
    return output_path


def generate_videos_for_pages(
    audio_results: dict[int, "TTSResult"],
    source_image: str | Path | None = None,
    use_cpu: bool | None = None,
) -> dict[int, Path]:
    """为每页音频生成数字人视频"""
    if use_cpu is None:
        use_cpu = should_use_cpu()

    results = {}
    for page_num, tts_result in audio_results.items():
        print(f"  生成第 {page_num + 1} 页数字人视频...")
        video_path = generate_video_sadtalker(
            audio_path=tts_result.audio_path,
            source_image=source_image,
            use_cpu=use_cpu,
        )
        results[page_num] = video_path

    return results
