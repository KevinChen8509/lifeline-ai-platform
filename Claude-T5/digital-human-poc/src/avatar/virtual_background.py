"""虚拟背景 - 替换数字人头像/视频的背景"""

import subprocess
from pathlib import Path

from src.config import BASE_DIR
from src.logger import get_logger

log = get_logger(__name__)

BACKGROUNDS_DIR = BASE_DIR / "assets" / "backgrounds"

# 内置虚拟背景预设（纯色渐变，FFmpeg 生成）
BUILTIN_BACKGROUNDS = {
    "无（保留原背景）": None,
    "纯白": "#FFFFFF",
    "浅灰": "#E0E0E0",
    "深蓝渐变": "gradient:deepblue",
    "暖灰渐变": "gradient:warmgray",
    "深色商务": "gradient:darkbiz",
}


def apply_virtual_background(
    input_path: str | Path,
    output_path: str | Path,
    background: str | None = None,
    color_key: str = "#00FF00",
    similarity: float = 0.3,
    blend: float = 0.1,
) -> Path:
    """
    为头像图片/视频替换虚拟背景

    使用 FFmpeg colorkey 滤镜抠掉指定颜色背景，叠加到新背景上。

    Args:
        input_path: 输入头像图片或视频
        output_path: 输出路径
        background: 背景图片路径，或内置预设名称，None=不处理
        color_key: 要抠除的颜色（十六进制），默认绿色
        similarity: 颜色相似度容差 (0.0~1.0)
        blend: 边缘融合度 (0.0~1.0)

    Returns:
        输出文件路径
    """
    if not background:
        # 无背景替换，直接返回原文件
        input_path = Path(input_path)
        output_path = Path(output_path)
        if input_path != output_path:
            import shutil
            output_path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(input_path, output_path)
        return output_path

    input_path = Path(input_path)
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    is_video = input_path.suffix.lower() in (".mp4", ".avi", ".mov", ".webm")

    # 解析背景类型
    bg_input_args, bg_filter = _build_background_input(background, input_path, is_video)

    if is_video:
        return _apply_bg_video(input_path, output_path, bg_input_args, bg_filter,
                               color_key, similarity, blend)
    else:
        return _apply_bg_image(input_path, output_path, bg_input_args, bg_filter,
                               color_key, similarity, blend)


def _build_background_input(background: str, input_path: Path, is_video: bool):
    """
    构建 FFmpeg 背景输入参数和滤镜

    Returns:
        (input_args: list[str], filter_chain: str)
    """
    # 检查是否为内置预设
    preset_color = BUILTIN_BACKGROUNDS.get(background)

    if preset_color is None:
        # 未知预设，视为文件路径
        bg_path = Path(background)
        if not bg_path.exists():
            return [], ""
        return (["-i", str(bg_path)], "[1:v]")

    if preset_color.startswith("gradient:"):
        # FFmpeg 渐变背景
        name = preset_color.split(":")[1]
        gradients = {
            "deepblue": "0x1a237e:0x283593",    # 深蓝
            "warmgray": "0xefebe9:0xbcaaa4",     # 暖灰
            "darkbiz":  "0x1b1b2f:0x162447",     # 深色商务
        }
        colors = gradients.get(name, "0x333333:0x666666")
        size = _probe_size(input_path) if not is_video else "256x256"
        return (
            ["-f", "lavfi", "-i", f"gradients=s={size}:c0={colors.split(':')[0]}:c1={colors.split(':')[1]}:d=1"],
            "[1:v]"
        )

    # 纯色背景
    size = _probe_size(input_path) if not is_video else "256x256"
    return (
        ["-f", "lavfi", "-i", f"color=c={preset_color}:s={size}:d=1"],
        "[1:v]"
    )


def _probe_size(path: Path) -> str:
    """探测图片/视频分辨率"""
    result = subprocess.run(
        ["ffprobe", "-v", "error",
         "-show_entries", "stream=width,height",
         "-of", "csv=s=x:p=0",
         str(path)],
        capture_output=True, text=True,
    )
    size = result.stdout.strip().split("\n")[0].strip()
    if "x" in size:
        return size
    return "256x256"


def _apply_bg_image(
    input_path: Path, output_path: Path,
    bg_input_args: list, bg_filter: str,
    color_key: str, similarity: float, blend: float,
) -> Path:
    """为静态图片替换背景"""
    if not bg_input_args:
        import shutil
        shutil.copy2(input_path, output_path)
        return output_path

    cmd = [
        "ffmpeg", "-y",
        "-i", str(input_path),
        *bg_input_args,
        "-filter_complex",
        (
            f"[0:v]colorkey={color_key}:{similarity}:{blend}[fg];"
            f"{bg_filter}scale=iw:ih[bg];"
            f"[bg][fg]overlay=0:0"
        ),
        str(output_path),
    ]

    log.info("  虚拟背景: {input_path.name} -> {output_path.name}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        # colorkey 失败则降级为直接拷贝
        log.warning("虚拟背景失败，保留原背景: {result.stderr[:200]}")
        import shutil
        shutil.copy2(input_path, output_path)
        return output_path

    return output_path


def _apply_bg_video(
    input_path: Path, output_path: Path,
    bg_input_args: list, bg_filter: str,
    color_key: str, similarity: float, blend: float,
) -> Path:
    """为视频替换背景"""
    if not bg_input_args:
        import shutil
        shutil.copy2(input_path, output_path)
        return output_path

    cmd = [
        "ffmpeg", "-y",
        "-i", str(input_path),
        *bg_input_args,
        "-filter_complex",
        (
            f"[0:v]colorkey={color_key}:{similarity}:{blend}[fg];"
            f"{bg_filter}scale=iw:ih[bg];"
            f"[bg][fg]overlay=0:0,format=yuv420p[out]"
        ),
        "-map", "[out]",
        "-map", "0:a?",
        "-c:v", "libx264",
        "-c:a", "copy",
        "-shortest",
        str(output_path),
    ]

    log.info("  虚拟背景(视频): {input_path.name} -> {output_path.name}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        log.warning("虚拟背景失败，保留原背景: {result.stderr[:200]}")
        import shutil
        shutil.copy2(input_path, output_path)
        return output_path

    return output_path


def apply_background_to_pages(
    page_videos: dict[int, Path],
    output_dir: str | Path,
    background: str | None = None,
    color_key: str = "#00FF00",
) -> dict[int, Path]:
    """
    批量为页面视频应用虚拟背景

    Args:
        page_videos: {页码: 视频路径}
        output_dir: 输出目录
        background: 背景图路径或预设名
        color_key: 抠除颜色

    Returns:
        {页码: 新视频路径}
    """
    if not background:
        return page_videos

    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    results = {}
    for pn, video_path in page_videos.items():
        out = output_dir / f"bg_page_{pn:03d}{Path(video_path).suffix}"
        results[pn] = apply_virtual_background(
            video_path, out,
            background=background,
            color_key=color_key,
        )
    return results
