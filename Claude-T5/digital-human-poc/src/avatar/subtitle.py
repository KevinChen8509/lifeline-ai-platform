"""字幕叠加 - 在视频上添加同步字幕"""

import re
import subprocess
from pathlib import Path


def _split_sentences(text: str, max_chars: int = 24) -> list[str]:
    """
    将文本按标点分句，每句不超过 max_chars 个字符。
    如果单句超长，强制截断。
    """
    # 按中英文句号、问号、感叹号、逗号、顿号分割
    parts = re.split(r'([。！？；，、\.\!\?;,])', text)
    sentences = []
    current = ""
    for part in parts:
        current += part
        if re.match(r'[。！？；\.\!\?;]', part) and current.strip():
            sentences.append(current.strip())
            current = ""
        elif len(current) >= max_chars:
            sentences.append(current.strip())
            current = ""
    if current.strip():
        sentences.append(current.strip())
    return sentences


def _generate_srt(
    sentences: list[str],
    total_duration: float,
    output_path: str | Path,
) -> Path:
    """
    生成 SRT 字幕文件，句子按时间均匀分配。

    Args:
        sentences: 分好的句子列表
        total_duration: 总时长（秒）
        output_path: SRT 文件输出路径
    """
    output_path = Path(output_path)
    if not sentences:
        return output_path

    # 每句分配的时长
    seg_duration = total_duration / len(sentences)

    with open(output_path, "w", encoding="utf-8") as f:
        for i, sent in enumerate(sentences):
            start = i * seg_duration
            end = (i + 1) * seg_duration
            # SRT 时间格式: HH:MM:SS,mmm
            f.write(f"{i + 1}\n")
            f.write(f"{_format_time(start)} --> {_format_time(end)}\n")
            f.write(f"{sent}\n\n")

    return output_path


def _format_time(seconds: float) -> str:
    """将秒数转为 SRT 时间格式 HH:MM:SS,mmm"""
    h = int(seconds // 3600)
    m = int((seconds % 3600) // 60)
    s = int(seconds % 60)
    ms = int((seconds % 1) * 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def burn_subtitles(
    video_path: str | Path,
    srt_path: str | Path,
    output_path: str | Path,
    font_size: int = 22,
    margin_v: int = 30,
) -> Path:
    """
    将 SRT 字幕烧录到视频上（硬字幕）

    Args:
        video_path: 输入视频
        srt_path: SRT 字幕文件
        output_path: 输出视频
        font_size: 字体大小
        margin_v: 字幕距底部距离（像素）
    """
    video_path = Path(video_path)
    srt_path = Path(srt_path)
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    # FFmpeg subtitles 滤镜需要转义路径中的特殊字符
    # Windows 路径需要将 \ 替换为 / 并加转义
    srt_escaped = str(srt_path.resolve()).replace("\\", "/").replace(":", "\\:")

    cmd = [
        "ffmpeg", "-y",
        "-i", str(video_path),
        "-vf", (
            f"subtitles='{srt_escaped}'"
            f":force_style='FontSize={font_size},"
            f"MarginV={margin_v},"
            f"PrimaryColour=&H00FFFFFF,"
            f"OutlineColour=&H00000000,"
            f"Outline=2,"
            f"Alignment=2'"
        ),
        "-c:v", "libx264",
        "-c:a", "copy",
        "-pix_fmt", "yuv420p",
        str(output_path),
    ]

    print(f"  烧录字幕: {video_path.name}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        # 如果 subtitles 滤镜失败（可能编译时不支持 libass），
        # 降级为直接拷贝（不烧录字幕）
        print(f"  警告: 字幕烧录失败，跳过字幕")
        import shutil
        shutil.copy2(video_path, output_path)
        return output_path

    return output_path


def add_subtitles_to_pages(
    page_videos: dict[int, Path],
    page_texts: dict[int, str],
    page_durations: dict[int, float],
    output_dir: str | Path,
) -> dict[int, Path]:
    """
    为每页视频生成并烧录字幕

    Args:
        page_videos: {页码: 视频路径}
        page_texts: {页码: 演讲文本}
        page_durations: {页码: 音频时长(秒)}
        output_dir: 输出目录

    Returns:
        {页码: 带字幕的视频路径}
    """
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    results = {}
    for page_num in sorted(page_videos.keys()):
        text = page_texts.get(page_num, "")
        duration = page_durations.get(page_num, 0)
        video = page_videos[page_num]

        if not text or duration <= 0:
            # 无文本或无时长，直接复制
            import shutil
            out = output_dir / f"sub_page_{page_num:03d}.mp4"
            shutil.copy2(video, out)
            results[page_num] = out
            continue

        # 分句 → 生成 SRT → 烧录
        sentences = _split_sentences(text)
        srt_path = output_dir / f"sub_page_{page_num:03d}.srt"
        _generate_srt(sentences, duration, srt_path)

        out = output_dir / f"sub_page_{page_num:03d}.mp4"
        results[page_num] = burn_subtitles(video, srt_path, out)

    return results
