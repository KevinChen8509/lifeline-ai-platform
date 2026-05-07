"""视频合并工具 - 将多段视频拼接为完整演示（支持转场效果）"""

import subprocess
from pathlib import Path


def _get_duration(video_path: Path) -> float:
    """获取视频时长（秒）"""
    result = subprocess.run(
        ["ffprobe", "-v", "error",
         "-show_entries", "format=duration",
         "-of", "default=noprint_wrappers=1:nokey=1",
         str(video_path)],
        capture_output=True, text=True,
    )
    try:
        return float(result.stdout.strip())
    except ValueError:
        return 0.0


def merge_videos(
    video_paths: list[Path],
    output_path: str | Path,
    transition: str = "fade",
    transition_duration: float = 0.5,
) -> Path:
    """
    将多个视频按页码顺序拼接为完整视频

    Args:
        video_paths: 按顺序排列的视频文件路径列表
        output_path: 输出路径
        transition: 转场效果 ("fade"=淡入淡出, "none"=无转场)
        transition_duration: 转场时长（秒）
    """
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    # 过滤不存在的文件
    valid_videos = [Path(vp) for vp in video_paths if Path(vp).exists()]
    if not valid_videos:
        raise RuntimeError("没有有效的视频文件可合并")

    # 单个视频直接拷贝
    if len(valid_videos) == 1:
        import shutil
        shutil.copy2(valid_videos[0], output_path)
        return output_path

    # 无转场: 用 concat 直接拼接
    if transition == "none":
        return _merge_concat(valid_videos, output_path)

    # 有转场: 用 xfade 滤镜
    return _merge_xfade(valid_videos, output_path, transition, transition_duration)


def _merge_concat(video_paths: list[Path], output_path: Path) -> Path:
    """直接拼接（无转场）"""
    concat_file = output_path.parent / "concat_list.txt"
    with open(concat_file, "w", encoding="utf-8") as f:
        for vp in video_paths:
            f.write(f"file '{vp.resolve()}'\n")

    cmd = [
        "ffmpeg", "-y",
        "-f", "concat", "-safe", "0",
        "-i", str(concat_file),
        "-c", "copy",
        str(output_path),
    ]
    print(f"  合并 {len(video_paths)} 段视频 -> {output_path.name}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"视频合并失败:\n{result.stderr}")
    concat_file.unlink(missing_ok=True)
    return output_path


def _merge_xfade(
    video_paths: list[Path],
    output_path: Path,
    transition: str,
    td: float,
) -> Path:
    """使用 xfade 滤镜合并视频（带转场）"""
    n = len(video_paths)
    durations = [_get_duration(vp) for vp in video_paths]

    # 构建输入参数
    inputs = []
    for vp in video_paths:
        inputs.extend(["-i", str(vp)])

    # 构建 filter_complex
    # 策略: 逐对应用 xfade，每对产生一个中间结果
    # [0:v][1:v]xfade=transition=fade:duration=0.5:offset=T0-0.5[v01];
    # [v01][2:v]xfade=transition=fade:duration=0.5:offset=(T0+T1-0.5)-0.5[v012];
    # ...
    filters = []
    audio_filters = []

    # 视频 xfade 链
    prev_label = "0:v"
    cumulative_offset = 0.0

    for i in range(1, n):
        # 当前片段的 offset = 之前累积时长 - 转场重叠
        offset = cumulative_offset + durations[i - 1] - td
        if offset < 0:
            offset = 0

        out_label = f"v{i}" if i < n - 1 else "vout"

        filters.append(
            f"[{prev_label}][{i}:v]xfade=transition={transition}"
            f":duration={td}:offset={offset}[{out_label}]"
        )

        cumulative_offset = offset
        prev_label = out_label

    # 音频 acrossfade 链
    prev_alabel = "0:a"
    for i in range(1, n):
        out_alabel = f"a{i}" if i < n - 1 else "aout"
        audio_filters.append(
            f"[{prev_alabel}][{i}:a]acrossfade=d={td}[{out_alabel}]"
        )
        prev_alabel = out_alabel

    filter_complex = ";".join(filters + audio_filters)

    cmd = [
        "ffmpeg", "-y",
        *inputs,
        "-filter_complex", filter_complex,
        "-map", "[vout]",
        "-map", "[aout]",
        "-c:v", "libx264",
        "-c:a", "aac",
        "-b:a", "192k",
        "-pix_fmt", "yuv420p",
        str(output_path),
    ]

    print(f"  合并 {n} 段视频 (转场: {transition}, {td}s) -> {output_path.name}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        # xfade 失败时降级为 concat
        print(f"  警告: 转场合并失败，降级为直接拼接")
        return _merge_concat(video_paths, output_path)

    return output_path
