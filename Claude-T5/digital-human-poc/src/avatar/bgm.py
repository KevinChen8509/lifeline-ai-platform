"""背景音乐混音 - 将 BGM 混入视频"""

import subprocess
from pathlib import Path

# 默认 BGM 文件
DEFAULT_BGM = Path(__file__).parent.parent.parent / "assets" / "bgm_default.wav"


def mix_bgm(
    video_path: str | Path,
    bgm_path: str | Path | None = None,
    output_path: str | Path | None = None,
    bgm_volume: float = 0.15,
    voice_volume: float = 1.0,
) -> Path:
    """
    将背景音乐混入视频，BGM 自动循环匹配视频时长

    Args:
        video_path: 输入视频（含语音）
        bgm_path: BGM 音频文件路径（默认使用 assets/bgm_default.wav）
        output_path: 输出视频路径（默认在原视频同目录加 _bgm 后缀）
        bgm_volume: BGM 音量 (0.0~1.0)
        voice_volume: 语音音量 (0.0~1.0)

    Returns:
        混音后的视频路径
    """
    video_path = Path(video_path)
    if bgm_path is None:
        bgm_path = DEFAULT_BGM
    bgm_path = Path(bgm_path)

    if output_path is None:
        output_path = video_path.parent / f"{video_path.stem}_bgm{video_path.suffix}"
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    if not bgm_path.exists():
        print(f"  警告: BGM 文件不存在 {bgm_path}，跳过混音")
        return video_path

    # FFmpeg amix 滤镜:
    # [0:a] 视频原音 → 调整音量
    # [1:a] BGM → 循环 → 调整音量 → 混合
    # -stream_loop -1 让 BGM 无限循环，-shortest 取视频长度
    cmd = [
        "ffmpeg", "-y",
        "-i", str(video_path),           # [0] 视频含音频
        "-stream_loop", "-1",            # BGM 无限循环
        "-i", str(bgm_path),             # [1] BGM
        "-filter_complex",
        (
            f"[0:a]volume={voice_volume}[voice];"
            f"[1:a]volume={bgm_volume}[bgm];"
            f"[voice][bgm]amix=inputs=2:duration=first:dropout_transition=2[aout]"
        ),
        "-map", "0:v",                   # 使用原视频画面
        "-map", "[aout]",                # 使用混合音频
        "-c:v", "copy",                  # 视频不重编码
        "-c:a", "aac",
        "-b:a", "192k",
        "-shortest",                     # 以最短流为准（视频长度）
        str(output_path),
    ]

    print(f"  混合背景音乐: {video_path.name}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"  警告: BGM 混音失败，使用原视频")
        return video_path

    return output_path
