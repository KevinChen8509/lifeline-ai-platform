"""
数字人汇报系统 - Web UI

启动: python web.py
访问: http://localhost:7860
"""

import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import gradio as gr

from src.config import OUTPUT_DIR, AVATAR_SOURCE_IMAGE
from src.parsers.ppt_parser import parse_ppt, slides_to_markdown
from src.parsers.slide_renderer import render_slides
from src.generators.script_generator import generate_script, parse_script_pages
from src.tts.engine import synthesize_speech, POPULAR_VOICES, EMOTION_PRESETS
from src.avatar.sadtalker_driver import (
    check_sadtalker_installed,
    generate_video_sadtalker,
    should_use_cpu,
)
from src.avatar.fallback_driver import generate_fallback_video
from src.avatar.compositor import composite_pages, _get_resolution
from src.avatar.bgm import mix_bgm
from src.avatar.merger import merge_videos, TRANSITION_CHOICES
from src.avatar.watermark import add_text_watermark, add_image_watermark
from src.avatar.cover import generate_cover, generate_slideshow_cover
from src.history import add_record, format_history_md
from src.detectors.language import detect_language, get_default_voice

# ============ 常量 ============

STYLE_CHOICES = {
    "正式汇报": "formal",
    "轻松讲解": "casual",
    "培训教学": "training",
}

VOICE_MAP = {desc: code for code, desc in POPULAR_VOICES.items()}

LAYOUT_CHOICES = {
    "画中画 (PPT+数字人)": "pip",
    "仅数字人": "avatar-only",
}

RATE_CHOICES = {
    "慢速 (-20%)": "-20%",
    "正常": "+0%",
    "快速 (+20%)": "+20%",
    "极快 (+50%)": "+50%",
}

LANGUAGE_CHOICES = {
    "自动检测": "auto",
    "中文": "zh",
    "English": "en",
}

EMOTION_MAP = {f"{v['desc']} ({k})": k for k, v in EMOTION_PRESETS.items()}

TRANSITION_MAP = {k: v for k, v in TRANSITION_CHOICES.items()}

POSITION_CHOICES = {
    "右下角": "bottom-right",
    "左下角": "bottom-left",
    "右上角": "top-right",
    "左上角": "top-left",
    "居中": "center",
}

RESOLUTION_MAP = {
    "720p (1280x720)": "720p",
    "1080p (1920x1080)": "1080p",
    "480p (854x480)": "480p",
    "4K (3840x2160)": "4K",
}

AVATAR_CHOICES = {
    "默认头像 (art_0)": "SadTalker/examples/source_image/art_0.png",
    "自定义上传": None,
}


def _merge_audios(audio_paths: list[str], output_path: str) -> str:
    """用 FFmpeg 合并多个音频文件"""
    concat_file = Path(output_path).parent / "audio_concat.txt"
    with open(concat_file, "w", encoding="utf-8") as f:
        for p in audio_paths:
            f.write(f"file '{Path(p).resolve()}'\n")
    cmd = [
        "ffmpeg", "-y", "-f", "concat", "-safe", "0",
        "-i", str(concat_file), "-c", "copy", output_path,
    ]
    subprocess.run(cmd, capture_output=True, text=True)
    concat_file.unlink(missing_ok=True)
    return output_path


# ============ 处理函数 ============


def _pick_ppt(ppt_file):
    """从单文件或多文件输入中取第一个 PPT 路径"""
    if ppt_file is None:
        return None
    if isinstance(ppt_file, list):
        return ppt_file[0] if ppt_file else None
    return ppt_file


def generate(
    ppt_file,
    style_name: str,
    voice_name: str,
    avatar_choice: str,
    avatar_upload,
    mode: str,
    layout_name: str,
    enable_subtitles: bool,
    enable_bgm: bool,
    rate_name: str,
    custom_bgm,
    language_name: str,
    emotion_name: str,
    transition_name: str,
    watermark_text: str,
    watermark_image,
    enable_cover: bool,
    resolution_name: str,
    enable_parallel: bool,
    enable_cache: bool,
    progress=gr.Progress(),
):
    """核心处理函数 — 生成器，流式更新 UI"""
    ppt_file = _pick_ppt(ppt_file)
    if ppt_file is None:
        yield ("请上传 PPT 文件", None, None, "请上传 PPT 文件", None)
        return

    export_paths = []  # 收集可下载素材

    start = time.time()

    # 准备参数
    style = STYLE_CHOICES.get(style_name, "formal")
    language = LANGUAGE_CHOICES.get(language_name, "auto")
    use_fallback = mode == "fallback" or "静态" in mode or "快速" in mode
    layout = LAYOUT_CHOICES.get(layout_name, "pip")
    rate = RATE_CHOICES.get(rate_name, "+0%")
    emotion = EMOTION_MAP.get(emotion_name, "default")
    transition = TRANSITION_MAP.get(transition_name, "fade")
    resolution = RESOLUTION_MAP.get(resolution_name, "720p")
    preset = EMOTION_PRESETS.get(emotion, EMOTION_PRESETS["default"])
    effective_rate = rate if rate != "+0%" else preset["rate"]

    # 头像
    if avatar_choice == "自定义上传" and avatar_upload is not None:
        avatar_path = avatar_upload
    else:
        avatar_path = AVATAR_CHOICES.get(avatar_choice, AVATAR_SOURCE_IMAGE)

    ppt_path = Path(ppt_file)
    output_dir = OUTPUT_DIR / f"web_{ppt_path.stem}_{int(start)}"
    output_dir.mkdir(parents=True, exist_ok=True)

    def status(msg):
        return f"{msg}  (已用时 {time.time() - start:.0f}s)"

    # === Step 1: 解析 PPT ===
    progress(0.05, desc="解析 PPT...")
    try:
        slides = parse_ppt(ppt_path)
    except Exception as e:
        yield (f"**解析失败**: {e}", None, None, f"错误: {e}", None)
        return

    markdown = slides_to_markdown(slides)

    # 检测语言
    if language == "auto":
        language = detect_language(markdown)
    lang_label = "中文" if language == "zh" else "English"

    # 英文内容自动切换英文语音
    if language == "en":
        voice = get_default_voice("en")
    else:
        voice = VOICE_MAP.get(voice_name, "zh-CN-YunxiNeural")

    info = f"**共 {len(slides)} 页** | 语言: {lang_label}\n\n"
    for s in slides:
        info += f"- 第{s.slide_index + 1}页: {s.title or '(无标题)'}\n"
    yield (info, None, None, status(f"解析完成: {len(slides)} 页 [{lang_label}]"), None)

    # === Step 1.5: 导出幻灯片图片 ===
    slide_images = []
    if layout == "pip":
        progress(0.1, desc="导出幻灯片图片...")
        try:
            slide_dir = output_dir / "slides"
            rw, rh = _get_resolution(resolution)
            slide_images = render_slides(ppt_path, slide_dir, width=rw, height=rh)
            yield (info, None, None, status(f"导出 {len(slide_images)} 张幻灯片"), None)
        except Exception as e:
            yield (info, None, None, status(f"幻灯片导出跳过: {e}"), None)
            layout = "avatar-only"

    # === Step 2: 生成演讲稿 ===
    progress(0.15, desc="生成演讲稿...")
    try:
        script = generate_script(markdown, style=style, language=language)
        script_pages = parse_script_pages(script)
    except Exception as e:
        yield (info + f"\n\n**生成失败**: {e}", None, None, f"错误: {e}", None)
        return

    script_display = f"**{len(script_pages)} 段演讲稿** [{lang_label}]\n\n---\n\n"
    for pn, text in sorted(script_pages.items()):
        script_display += f"### 第 {pn + 1} 页\n{text}\n\n---\n\n"
    script_file = output_dir / "script.txt"
    script_file.write_text(script, encoding="utf-8")
    export_paths.append(str(script_file))
    yield (script_display, None, None, status(f"演讲稿完成: {len(script_pages)} 段"), None)

    # === Step 3+4: TTS + 视频 逐页流水线 ===
    progress(0.3, desc="逐页合成音频+视频...")
    audio_dir = output_dir / "audio"
    audio_dir.mkdir(parents=True, exist_ok=True)
    video_dir = output_dir / "video"
    video_dir.mkdir(parents=True, exist_ok=True)

    audio_paths = []
    videos = {}
    page_nums = sorted(script_pages.keys())
    total_pages = len(page_nums)

    for pn in page_nums:
        text = script_pages[pn]
        page_idx = page_nums.index(pn)
        page_pct = 0.3 + 0.55 * page_idx / total_pages

        # TTS
        progress(page_pct, desc=f"第 {pn + 1}/{total_pages} 页: 合成语音...")
        audio_out = audio_dir / f"page_{pn:03d}.mp3"
        result = synthesize_speech(text, audio_out, voice=voice, rate=effective_rate,
                                   pitch=preset["pitch"], volume=preset["volume"],
                                   use_cache=enable_cache)
        audio_paths.append(result.audio_path)

        # 视频
        progress(page_pct + 0.25 / total_pages, desc=f"第 {pn + 1}/{total_pages} 页: 生成视频...")
        video_out = video_dir / f"page_{pn:03d}.mp4"
        if use_fallback:
            videos[pn] = generate_fallback_video(
                audio_path=str(audio_out),
                source_image=avatar_path,
                output_path=video_out,
            )
        elif check_sadtalker_installed():
            cpu = should_use_cpu()
            videos[pn] = generate_video_sadtalker(
                audio_path=str(audio_out),
                output_path=video_out,
                source_image=avatar_path,
                use_cpu=cpu,
            )
        else:
            yield (script_display, None, None,
                   "SadTalker 未安装，请选择快速模式", None)
            return

        # 流式: 每完成一页就展示当前页视频
        if videos[pn]:
            yield (script_display, audio_paths[-1], str(videos[pn]),
                   status(f"第 {pn + 1}/{total_pages} 页完成"), None)

    # 合并所有音频
    merged_audio = str(output_dir / "merged_audio.mp3")
    _merge_audios(audio_paths, merged_audio)
    export_paths.extend(audio_paths)
    yield (script_display, merged_audio, str(videos[page_nums[-1]]),
           status(f"音频+视频完成: {total_pages} 页"), None)

    # === Step 4.5: 画中画合成 ===
    if layout == "pip" and slide_images:
        progress(0.9, desc="合成画中画...")
        pip_dir = output_dir / "pip"
        videos = composite_pages(slide_images, videos, pip_dir, resolution=resolution)

    # === Step 4.6: 字幕叠加 ===
    if enable_subtitles:
        progress(0.93, desc="叠加字幕...")
        from src.avatar.subtitle import add_subtitles_to_pages, _split_sentences, _generate_srt
        page_texts = dict(sorted(script_pages.items()))
        page_durations = {}
        for pn in sorted(script_pages.keys()):
            audio_p = audio_dir / f"page_{pn:03d}.mp3"
            import subprocess as sp
            probe = sp.run(
                ["ffprobe", "-v", "error", "-show_entries", "format=duration",
                 "-of", "default=noprint_wrappers=1:nokey=1", str(audio_p)],
                capture_output=True, text=True,
            )
            try:
                page_durations[pn] = float(probe.stdout.strip())
            except ValueError:
                page_durations[pn] = len(script_pages[pn]) / 4.5
        sub_dir = output_dir / "subtitle"
        sub_dir.mkdir(parents=True, exist_ok=True)
        # 生成 SRT 文件供下载
        srt_parts = []
        srt_idx = 1
        for pn in sorted(script_pages.keys()):
            text = page_texts.get(pn, "")
            dur = page_durations.get(pn, 0)
            if text and dur > 0:
                sentences = _split_sentences(text)
                srt_file = sub_dir / f"page_{pn:03d}.srt"
                _generate_srt(sentences, dur, srt_file)
                export_paths.append(str(srt_file))
                srt_parts.append(srt_file.read_text(encoding="utf-8"))
        # 合并所有页的 SRT 为完整字幕文件
        if srt_parts:
            combined_srt = output_dir / "full_subtitle.srt"
            combined_srt.write_text("\n".join(srt_parts), encoding="utf-8")
            export_paths.append(str(combined_srt))
        videos = add_subtitles_to_pages(videos, page_texts, page_durations, sub_dir)

    # 合并视频
    progress(0.93, desc="合并视频...")
    sorted_pns = sorted(videos.keys())
    video_list = [videos[p] for p in sorted_pns]
    if len(video_list) > 1:
        final = output_dir / "final.mp4"
        merge_videos(video_list, final, transition=transition)
    else:
        final = video_list[0]

    # 混合背景音乐
    if enable_bgm:
        progress(0.97, desc="混合背景音乐...")
        final = mix_bgm(final, bgm_path=custom_bgm or None)

    # 水印
    if watermark_text and watermark_text.strip():
        progress(0.98, desc="添加水印...")
        try:
            wm_path = output_dir / "final_wm.mp4"
            final = add_text_watermark(final, text=watermark_text.strip(), output_path=wm_path)
        except Exception as e:
            yield (script_display, merged_audio, str(final),
                   f"水印添加失败: {e}", export_paths)
    elif watermark_image:
        progress(0.98, desc="添加图片水印...")
        try:
            wm_path = output_dir / "final_wm.mp4"
            final = add_image_watermark(final, image_path=watermark_image, output_path=wm_path)
        except Exception as e:
            yield (script_display, merged_audio, str(final),
                   f"水印添加失败: {e}", export_paths)

    # 封面
    if enable_cover:
        try:
            if slide_images:
                cover_path = generate_slideshow_cover(slide_images, output_dir / "cover.png")
            else:
                cover_path = generate_cover(final, output_dir / "cover.png")
            export_paths.append(str(cover_path))
        except Exception:
            pass

    elapsed = time.time() - start
    export_paths.append(str(final))
    # 记录历史
    add_record(
        ppt_name=ppt_path.name,
        mode=mode,
        style=style,
        voice=voice,
        output_dir=str(output_dir),
        video_path=str(final),
        audio_path=merged_audio,
        script_path=str(output_dir / "script.txt"),
        duration_seconds=elapsed,
        pages=len(script_pages),
    )
    yield (script_display, merged_audio, str(final),
           f"完成! 总耗时 {elapsed:.0f}s", export_paths)


def generate_preview(
    ppt_file,
    style_name: str,
    voice_name: str,
    avatar_choice: str,
    avatar_upload,
    rate_name: str,
    language_name: str,
    emotion_name: str,
    progress=gr.Progress(),
):
    """快速预览 — 每页只取前 50 字，静态图片模式，跳过 PIP/字幕/BGM"""
    ppt_file = _pick_ppt(ppt_file)
    if ppt_file is None:
        yield ("请上传 PPT 文件", None, None, "请上传 PPT 文件", None)
        return

    start = time.time()
    style = STYLE_CHOICES.get(style_name, "formal")
    rate = RATE_CHOICES.get(rate_name, "+0%")
    language = LANGUAGE_CHOICES.get(language_name, "auto")

    if avatar_choice == "自定义上传" and avatar_upload is not None:
        avatar_path = avatar_upload
    else:
        avatar_path = AVATAR_CHOICES.get(avatar_choice, AVATAR_SOURCE_IMAGE)

    ppt_path = Path(ppt_file)
    output_dir = OUTPUT_DIR / f"preview_{ppt_path.stem}_{int(start)}"
    output_dir.mkdir(parents=True, exist_ok=True)

    def status(msg):
        return f"[预览] {msg}  (已用时 {time.time() - start:.0f}s)"

    # Step 1: 解析 PPT
    progress(0.1, desc="解析 PPT...")
    try:
        slides = parse_ppt(ppt_path)
    except Exception as e:
        yield (f"**解析失败**: {e}", None, None, f"错误: {e}", None)
        return

    markdown = slides_to_markdown(slides)
    if language == "auto":
        language = detect_language(markdown)
    lang_label = "中文" if language == "zh" else "English"
    voice = get_default_voice(language) if language == "en" else VOICE_MAP.get(voice_name, "zh-CN-YunxiNeural")

    info = f"**[预览模式] 共 {len(slides)} 页** [{lang_label}]\n\n"
    for s in slides:
        info += f"- 第{s.slide_index + 1}页: {s.title or '(无标题)'}\n"
    yield (info, None, None, status(f"解析完成: {len(slides)} 页"), None)

    # Step 2: 生成演讲稿
    progress(0.2, desc="生成演讲稿...")
    try:
        script = generate_script(markdown, style=style, language=language)
        script_pages = parse_script_pages(script)
    except Exception as e:
        yield (info + f"\n\n**生成失败**: {e}", None, None, f"错误: {e}", None)
        return

    # 截断: 每页只取前 50 字
    preview_pages = {}
    for pn, text in script_pages.items():
        truncated = text[:50]
        if len(text) > 50:
            truncated += "……"
        preview_pages[pn] = truncated

    script_display = f"**[预览] {len(preview_pages)} 段 (每段截取前50字)**\n\n---\n\n"
    for pn, text in sorted(preview_pages.items()):
        script_display += f"### 第 {pn + 1} 页\n{text}\n\n---\n\n"
    yield (script_display, None, None, status("演讲稿完成 (截断)"), None)

    # Step 3: TTS
    progress(0.4, desc="合成预览语音...")
    audio_dir = output_dir / "audio"
    audio_dir.mkdir(parents=True, exist_ok=True)

    audio_paths = []
    for pn in sorted(preview_pages.keys()):
        text = preview_pages[pn]
        out = audio_dir / f"page_{pn:03d}.mp3"
        result = synthesize_speech(text, out, voice=voice, rate=rate)
        audio_paths.append(result.audio_path)

    merged_audio = str(output_dir / "merged_audio.mp3")
    _merge_audios(audio_paths, merged_audio)
    yield (script_display, merged_audio, None, status("语音合成完成"), None)
    yield (script_display, merged_audio, None, status("语音合成完成"), None)

    # Step 4: 静态图片视频
    progress(0.6, desc="生成预览视频...")
    video_dir = output_dir / "video"
    video_dir.mkdir(parents=True, exist_ok=True)

    videos = {}
    for pn in sorted(preview_pages.keys()):
        n = len(preview_pages)
        progress(0.6 + 0.3 * (pn + 1) / n, desc=f"预览第 {pn + 1} 页...")
        audio_p = audio_dir / f"page_{pn:03d}.mp3"
        out = video_dir / f"page_{pn:03d}.mp4"
        videos[pn] = generate_fallback_video(
            audio_path=str(audio_p),
            source_image=avatar_path,
            output_path=out,
        )

    # 合并
    progress(0.95, desc="合并...")
    sorted_pns = sorted(videos.keys())
    video_list = [videos[p] for p in sorted_pns]
    if len(video_list) > 1:
        final = output_dir / "preview.mp4"
        merge_videos(video_list, final, transition="none")
    else:
        final = video_list[0]

    elapsed = time.time() - start
    add_record(
        ppt_name=ppt_path.name,
        mode="预览",
        style=style,
        voice=voice,
        output_dir=str(output_dir),
        video_path=str(final),
        audio_path=merged_audio,
        duration_seconds=elapsed,
        pages=len(preview_pages),
        is_preview=True,
    )
    yield (script_display, merged_audio, str(final),
           f"预览完成! 耗时 {elapsed:.0f}s", None)


def generate_batch(
    ppt_files,
    style_name: str,
    voice_name: str,
    avatar_choice: str,
    avatar_upload,
    mode: str,
    layout_name: str,
    enable_subtitles: bool,
    enable_bgm: bool,
    rate_name: str,
    custom_bgm,
    language_name: str,
    emotion_name: str,
    progress=gr.Progress(),
):
    """批量处理多个 PPT 文件"""
    if not ppt_files:
        yield ("请上传 PPT 文件", None, None, "请上传 PPT 文件", None)
        return

    if isinstance(ppt_files, str):
        ppt_files = [ppt_files]

    style = STYLE_CHOICES.get(style_name, "formal")
    voice = VOICE_MAP.get(voice_name, "zh-CN-YunxiNeural")
    rate = RATE_CHOICES.get(rate_name, "+0%")
    emotion = EMOTION_MAP.get(emotion_name, "default")
    preset = EMOTION_PRESETS.get(emotion, EMOTION_PRESETS["default"])
    use_fallback = mode == "fallback" or "静态" in mode or "快速" in mode
    layout = LAYOUT_CHOICES.get(layout_name, "pip")

    if avatar_choice == "自定义上传" and avatar_upload is not None:
        avatar_path = avatar_upload
    else:
        avatar_path = AVATAR_CHOICES.get(avatar_choice, AVATAR_SOURCE_IMAGE)

    total = len(ppt_files)
    batch_start = time.time()
    all_outputs = []
    results_md = f"**批量处理 {total} 个文件**\n\n---\n\n"

    yield (results_md + "准备中...", None, None, "开始批量处理...", None)

    for idx, ppt_file in enumerate(ppt_files):
        ppt_path = Path(ppt_file)
        pct_base = idx / total

        progress(pct_base, desc=f"[{idx + 1}/{total}] 处理 {ppt_path.name}...")

        try:
            output_dir = OUTPUT_DIR / f"batch_{ppt_path.stem}_{int(batch_start)}"
            output_dir.mkdir(parents=True, exist_ok=True)

            # 解析 PPT
            slides = parse_ppt(ppt_path)
            markdown = slides_to_markdown(slides)

            # 生成演讲稿
            script = generate_script(markdown, style=style)
            script_pages = parse_script_pages(script)
            (output_dir / "script.txt").write_text(script, encoding="utf-8")

            # TTS
            audio_dir = output_dir / "audio"
            audio_dir.mkdir(parents=True, exist_ok=True)
            audio_paths = []
            for pn in sorted(script_pages.keys()):
                out = audio_dir / f"page_{pn:03d}.mp3"
                result = synthesize_speech(script_pages[pn], out, voice=voice, rate=rate,
                                            pitch=preset["pitch"], volume=preset["volume"])
                audio_paths.append(result.audio_path)

            merged_audio = str(output_dir / "merged_audio.mp3")
            _merge_audios(audio_paths, merged_audio)

            # 视频
            video_dir = output_dir / "video"
            video_dir.mkdir(parents=True, exist_ok=True)
            videos = {}
            for pn in sorted(script_pages.keys()):
                audio_p = audio_dir / f"page_{pn:03d}.mp3"
                out = video_dir / f"page_{pn:03d}.mp4"
                videos[pn] = generate_fallback_video(
                    audio_path=str(audio_p),
                    source_image=avatar_path,
                    output_path=out,
                )

            # 合并
            sorted_pns = sorted(videos.keys())
            video_list = [videos[p] for p in sorted_pns]
            if len(video_list) > 1:
                final = output_dir / "final.mp4"
                merge_videos(video_list, final, transition="none")
            else:
                final = video_list[0]

            # BGM
            if enable_bgm:
                final = mix_bgm(final, bgm_path=custom_bgm or None)

            all_outputs.append(str(final))
            results_md += f"**[{idx + 1}] {ppt_path.name}** - 完成\n\n"

        except Exception as e:
            results_md += f"**[{idx + 1}] {ppt_path.name}** - 失败: {e}\n\n"

        pct = (idx + 1) / total
        progress(pct, desc=f"完成 {idx + 1}/{total}")
        yield (results_md, None, None,
               f"批量处理: {idx + 1}/{total} 完成", None)

    elapsed = time.time() - batch_start
    yield (results_md, None, None,
           f"批量完成! {total} 个文件, 耗时 {elapsed:.0f}s", all_outputs)


# ============ 构建 UI ============

def build_ui():
    sadtalker_ok = check_sadtalker_installed()
    gpu_ok = not should_use_cpu() if sadtalker_ok else False

    with gr.Blocks(title="数字人汇报系统") as app:
        gr.Markdown(
            "# 数字人汇报系统\n"
            "上传 PPT 文件，自动生成演讲稿、语音和数字人视频"
        )

        with gr.Row():
            # === 左侧: 配置 ===
            with gr.Column(scale=1):
                gr.Markdown("### 配置")

                ppt_input = gr.File(
                    label="上传 PPT（支持多个文件）",
                    file_types=[".pptx"],
                    type="filepath",
                    file_count="multiple",
                )

                style_dd = gr.Dropdown(
                    choices=list(STYLE_CHOICES.keys()),
                    value="正式汇报",
                    label="演讲风格",
                )

                language_dd = gr.Dropdown(
                    choices=list(LANGUAGE_CHOICES.keys()),
                    value="自动检测",
                    label="演讲语言",
                )

                emotion_dd = gr.Dropdown(
                    choices=list(EMOTION_MAP.keys()),
                    value="默认（中性） (default)",
                    label="语音情感",
                )

                voice_dd = gr.Dropdown(
                    choices=list(VOICE_MAP.keys()),
                    value="中文男声-云希（年轻阳光）",
                    label="语音",
                )

                rate_dd = gr.Dropdown(
                    choices=list(RATE_CHOICES.keys()),
                    value="正常",
                    label="语速",
                )

                avatar_radio = gr.Radio(
                    choices=list(AVATAR_CHOICES.keys()),
                    value="默认头像 (art_0)",
                    label="头像",
                )

                avatar_file = gr.File(
                    label="上传自定义头像 (正面人脸照片)",
                    file_types=["image"],
                    type="filepath",
                    visible=False,
                )

                def toggle_avatar(choice):
                    return gr.update(visible=choice == "自定义上传")

                avatar_radio.change(
                    fn=toggle_avatar,
                    inputs=avatar_radio,
                    outputs=avatar_file,
                )

                mode_choices = ["快速模式 (静态图片)"]
                if sadtalker_ok:
                    label = "SadTalker (GPU)" if gpu_ok else "SadTalker (CPU, 较慢)"
                    mode_choices.append(label)

                mode_radio = gr.Radio(
                    choices=mode_choices,
                    value="快速模式 (静态图片)",
                    label="驱动模式",
                )

                if sadtalker_ok and not gpu_ok:
                    gr.Markdown("*提示: CPU 模式较慢 (~32s/帧)*")

                layout_dd = gr.Dropdown(
                    choices=list(LAYOUT_CHOICES.keys()),
                    value="画中画 (PPT+数字人)",
                    label="视频布局",
                )

                subtitle_cb = gr.Checkbox(
                    value=True,
                    label="叠加字幕",
                )

                bgm_cb = gr.Checkbox(
                    value=True,
                    label="背景音乐",
                )

                bgm_file = gr.File(
                    label="自定义 BGM（留空用默认）",
                    file_types=[".mp3", ".wav", ".ogg", ".m4a"],
                    type="filepath",
                )

                transition_dd = gr.Dropdown(
                    choices=list(TRANSITION_MAP.keys()),
                    value="淡入淡出 (fade)",
                    label="转场效果",
                )

                wm_text = gr.Textbox(
                    label="文字水印（留空不加）",
                    placeholder="如: 公司名/汇报人",
                )

                wm_image = gr.File(
                    label="图片水印/Logo（留空不加）",
                    file_types=["image"],
                    type="filepath",
                )

                cover_cb = gr.Checkbox(
                    value=True,
                    label="生成封面图",
                )

                resolution_dd = gr.Dropdown(
                    choices=list(RESOLUTION_MAP.keys()),
                    value="720p (1280x720)",
                    label="输出分辨率",
                )

                parallel_cb = gr.Checkbox(
                    value=False,
                    label="并行处理（加速 TTS+视频生成）",
                )

                cache_cb = gr.Checkbox(
                    value=True,
                    label="TTS 缓存（相同文本跳过重复合成）",
                )

                gen_btn = gr.Button("生成", variant="primary", size="lg")
                preview_btn = gr.Button("快速预览", size="lg")
                batch_btn = gr.Button("批量生成", size="lg")
                status_box = gr.Textbox(label="状态", interactive=False)

            # === 右侧: 结果 ===
            with gr.Column(scale=2):
                gr.Markdown("### 结果")

                with gr.Tabs():
                    with gr.Tab("演讲稿"):
                        script_out = gr.Markdown(
                            "上传 PPT 并点击「生成」"
                        )

                    with gr.Tab("音频"):
                        audio_out = gr.Audio(label="演讲音频",
                                             type="filepath")

                    with gr.Tab("视频"):
                        video_out = gr.Video(label="数字人视频")

                    with gr.Tab("素材下载"):
                        export_files = gr.Files(label="可下载素材")

                    with gr.Tab("历史记录"):
                        history_md = gr.Markdown(format_history_md())
                        refresh_btn = gr.Button("刷新历史")
                        refresh_btn.click(
                            fn=lambda: format_history_md(),
                            inputs=[], outputs=[history_md],
                        )

        # 绑定
        gen_btn.click(
            fn=generate,
            inputs=[ppt_input, style_dd, voice_dd,
                    avatar_radio, avatar_file, mode_radio, layout_dd,
                    subtitle_cb, bgm_cb, rate_dd, bgm_file, language_dd, emotion_dd,
                    transition_dd, wm_text, wm_image, cover_cb,
                    resolution_dd, parallel_cb, cache_cb],
            outputs=[script_out, audio_out, video_out, status_box, export_files],
        )
        preview_btn.click(
            fn=generate_preview,
            inputs=[ppt_input, style_dd, voice_dd,
                    avatar_radio, avatar_file, rate_dd, language_dd, emotion_dd],
            outputs=[script_out, audio_out, video_out, status_box, export_files],
        )
        batch_btn.click(
            fn=generate_batch,
            inputs=[ppt_input, style_dd, voice_dd,
                    avatar_radio, avatar_file, mode_radio, layout_dd,
                    subtitle_cb, bgm_cb, rate_dd, bgm_file, language_dd, emotion_dd],
            outputs=[script_out, audio_out, video_out, status_box, export_files],
        )

    return app


# ============ 启动 ============

if __name__ == "__main__":
    app = build_ui()
    print("\n" + "=" * 50)
    print("  数字人汇报系统 Web UI")
    print("  访问: http://localhost:7860")
    print("=" * 50 + "\n")
    app.launch(
        server_name="0.0.0.0",
        server_port=7860,
        share=False,
        show_error=True,
    )
