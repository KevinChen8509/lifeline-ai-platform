"""测试 BGM 混音模块"""

from pathlib import Path


def test_mix_bgm_default_not_found(tmp_output):
    """BGM 文件不存在时返回原视频"""
    from src.avatar.bgm import mix_bgm

    # 创建一个假视频
    fake_video = tmp_output / "test.mp4"
    fake_video.write_text("fake")

    result = mix_bgm(fake_video, bgm_path=tmp_output / "nonexistent.wav")
    # 应返回原路径（BGM 不存在）
    assert result == fake_video


def test_mix_bgm_with_custom_bgm(tmp_output):
    """使用自定义 BGM 混音（需要 FFmpeg）"""
    import subprocess
    from src.avatar.bgm import mix_bgm

    # 检查 ffmpeg 是否可用
    r = subprocess.run(["ffmpeg", "-version"], capture_output=True)
    if r.returncode != 0:
        return  # 跳过

    # 生成一个 1s 的测试视频
    test_video = tmp_output / "test.mp4"
    subprocess.run([
        "ffmpeg", "-y", "-f", "lavfi", "-i", "anullsrc=r=22050:cl=mono",
        "-t", "1", "-c:a", "aac", str(test_video)
    ], capture_output=True)

    if not test_video.exists():
        return

    # 生成一个 1s 的测试 BGM
    test_bgm = tmp_output / "bgm.wav"
    subprocess.run([
        "ffmpeg", "-y", "-f", "lavfi", "-i", "sine=f=440:d=1",
        str(test_bgm)
    ], capture_output=True)

    if not test_bgm.exists():
        return

    output = tmp_output / "output.mp4"
    result = mix_bgm(test_video, bgm_path=test_bgm, output_path=output)
    assert result.exists()
    assert result.stat().st_size > 0
