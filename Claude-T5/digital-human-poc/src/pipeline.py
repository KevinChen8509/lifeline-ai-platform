"""数字人汇报系统 - 主流水水线"""

import time
from pathlib import Path
from typing import Callable

from src.config import OUTPUT_DIR, TEMPLATES_DIR
from src.parsers.ppt_parser import parse_ppt, slides_to_markdown, slides_to_json
from src.parsers.slide_renderer import render_slides
from src.detectors.language import detect_language, get_default_voice
from src.generators.script_generator import generate_script, parse_script_pages
from src.tts.engine import synthesize_pages, synthesize_speech, EMOTION_PRESETS
from src.avatar.sadtalker_driver import (
    generate_videos_for_pages,
    check_sadtalker_installed,
    should_use_cpu,
)
from src.avatar.fallback_driver import generate_fallback_video
from src.avatar.compositor import composite_pages
from src.avatar.subtitle import add_subtitles_to_pages
from src.avatar.bgm import mix_bgm
from src.avatar.merger import merge_videos
from src.avatar.watermark import add_text_watermark, add_image_watermark
from src.avatar.cover import generate_cover, generate_slideshow_cover


class DigitalHumanPipeline:
    """数字人汇报生成流水线"""

    def __init__(self):
        self.slides = []
        self.script = ""
        self.script_pages = {}
        self.audio_results = {}
        self.video_results = {}
        self.slide_images = []
        self.output_dir = OUTPUT_DIR

    def run(
        self,
        ppt_path: str | Path,
        style: str = "formal",
        avatar_image: str | Path | None = None,
        output_name: str | None = None,
        skip_avatar: bool = False,
        use_cpu: bool | None = None,
        use_fallback: bool = False,
        layout: str = "pip",
        subtitles: bool = True,
        bgm: bool = True,
        bgm_path: str | Path | None = None,
        rate: str = "+0%",
        language: str = "auto",
        emotion: str = "default",
        transition: str = "fade",
        watermark_text: str | None = None,
        watermark_image: str | Path | None = None,
        generate_cover_image: bool = True,
        progress_callback: Callable[[str, float], None] | None = None,
    ) -> Path:
        """
        运行完整流水线

        Args:
            ppt_path: PPT 文件路径
            style: 演讲风格 (formal/casual/training)
            avatar_image: 头像图片路径
            output_name: 输出文件名
            skip_avatar: 跳过数字人生成，仅生成音频
            use_cpu: 强制使用 CPU 模式 (None=自动检测)
            use_fallback: 强制使用 FFmpeg 静态图片模式
            layout: 视频布局 (pip=画中画, avatar-only=仅数字人)
            progress_callback: 进度回调 (message, progress_0_to_1)
        """
        def _progress(msg: str, pct: float):
            print(msg)
            if progress_callback:
                progress_callback(msg, pct)

        ppt_path = Path(ppt_path)
        start = time.time()

        # 设置输出目录
        stem = ppt_path.stem
        self.output_dir = OUTPUT_DIR / stem
        self.output_dir.mkdir(parents=True, exist_ok=True)

        _progress(f"开始处理: {ppt_path.name}", 0.0)

        # Step 1: 解析 PPT
        _progress("[1/5] 解析 PPT 内容...", 0.05)
        self.slides = parse_ppt(ppt_path)
        markdown = slides_to_markdown(self.slides)

        (self.output_dir / "parsed_slides.json").write_text(
            slides_to_json(self.slides), encoding="utf-8"
        )
        (self.output_dir / "parsed_slides.md").write_text(
            markdown, encoding="utf-8"
        )
        _progress(f"  解析完成: {len(self.slides)} 页", 0.1)

        # 检测语言
        if language == "auto":
            language = detect_language(markdown)
        _progress(f"  检测语言: {'中文' if language == 'zh' else 'English'}", 0.11)

        # Step 1.5: 导出幻灯片图片
        _progress("[2/5] 导出幻灯片图片...", 0.12)
        slide_dir = self.output_dir / "slides"
        try:
            self.slide_images = render_slides(ppt_path, slide_dir)
            _progress(f"  导出完成: {len(self.slide_images)} 张", 0.2)
        except Exception as e:
            _progress(f"  幻灯片导出跳过: {e}", 0.2)
            self.slide_images = []
            layout = "avatar-only"

        # Step 2: 生成演讲稿
        _progress("[3/5] 生成演讲稿...", 0.25)
        self.script = generate_script(markdown, style=style, language=language)
        self.script_pages = parse_script_pages(self.script)

        (self.output_dir / "script.txt").write_text(
            self.script, encoding="utf-8"
        )
        _progress(f"  演讲稿生成完成: {len(self.script_pages)} 段", 0.35)

        # Step 3: TTS 语音合成
        _progress("[4/5] 合成语音...", 0.4)
        audio_dir = self.output_dir / "audio"
        # 应用情感预设
        preset = EMOTION_PRESETS.get(emotion, EMOTION_PRESETS["default"])
        effective_rate = rate if rate != "+0%" else preset["rate"]
        _progress(f"  情感: {preset['desc']}", 0.42)

        self.audio_results = synthesize_pages(
            self.script_pages, audio_dir,
            rate=effective_rate, pitch=preset["pitch"], volume=preset["volume"],
        )

        total_duration = sum(r.duration_seconds for r in self.audio_results.values())
        _progress(f"  语音合成完成: 总时长约 {total_duration:.0f} 秒", 0.5)

        if skip_avatar:
            _progress("完成 (仅音频模式)", 1.0)
            return self.output_dir

        # Step 4: 数字人视频生成
        _progress("[5/5] 生成数字人视频...", 0.55)

        use_sadtalker = check_sadtalker_installed() and not use_fallback

        if use_sadtalker:
            auto_cpu = should_use_cpu() if use_cpu is None else use_cpu
            mode = "CPU" if auto_cpu else "GPU"
            _progress(f"  使用 SadTalker [{mode} 模式]...", 0.6)
            self.video_results = generate_videos_for_pages(
                self.audio_results, source_image=avatar_image, use_cpu=use_cpu
            )
        else:
            label = "静态图片" if use_fallback else "SadTalker 未安装"
            _progress(f"  使用 {label} 模式...", 0.6)
            self.video_results = {}
            for page_num, tts_result in self.audio_results.items():
                n = len(self.audio_results)
                page_pct = 0.6 + 0.25 * (page_num + 1) / n
                _progress(f"  生成第 {page_num + 1} 页视频...", page_pct)
                video_path = generate_fallback_video(
                    audio_path=tts_result.audio_path,
                    source_image=avatar_image,
                    output_path=self.output_dir / "video" / f"page_{page_num:03d}.mp4",
                )
                self.video_results[page_num] = video_path

        # Step 4.5: 画中画合成
        if layout == "pip" and self.slide_images:
            _progress("  合成画中画...", 0.88)
            pip_dir = self.output_dir / "pip"
            self.video_results = composite_pages(
                self.slide_images, self.video_results, pip_dir
            )

        # Step 4.6: 字幕叠加
        if subtitles and self.script_pages:
            _progress("  叠加字幕...", 0.91)
            page_texts = dict(sorted(self.script_pages.items()))
            page_durations = {
                pn: r.duration_seconds
                for pn, r in self.audio_results.items()
            }
            sub_dir = self.output_dir / "subtitle"
            self.video_results = add_subtitles_to_pages(
                self.video_results, page_texts, page_durations, sub_dir
            )

        # 合并视频
        if self.video_results:
            sorted_pages = sorted(self.video_results.keys())
            video_list = [self.video_results[p] for p in sorted_pages]

            _progress("合并视频...", 0.93)
            if len(video_list) > 1:
                final_output = self.output_dir / (output_name or f"{stem}_final.mp4")
                final_path = merge_videos(video_list, final_output, transition=transition)
            else:
                final_path = video_list[0]

            # Step 5: 混合背景音乐
            if bgm:
                _progress("混合背景音乐...", 0.97)
                final_path = mix_bgm(final_path, bgm_path=bgm_path)

            # Step 6: 水印
            if watermark_text:
                _progress("添加文字水印...", 0.98)
                wm_path = self.output_dir / f"{stem}_wm.mp4"
                final_path = add_text_watermark(final_path, text=watermark_text, output_path=wm_path)
            elif watermark_image:
                _progress("添加图片水印...", 0.98)
                wm_path = self.output_dir / f"{stem}_wm.mp4"
                final_path = add_image_watermark(final_path, image_path=watermark_image, output_path=wm_path)

            # Step 7: 封面
            if generate_cover_image:
                _progress("生成封面...", 0.99)
                try:
                    if self.slide_images:
                        generate_slideshow_cover(self.slide_images, self.output_dir / "cover.png")
                    else:
                        generate_cover(final_path, self.output_dir / "cover.png")
                except Exception as e:
                    _progress(f"  封面生成跳过: {e}", 0.99)

            elapsed = time.time() - start
            _progress(f"完成! 总耗时 {elapsed:.0f}s", 1.0)
            return final_path

        elapsed = time.time() - start
        _progress(f"完成! 耗时 {elapsed:.0f}s", 1.0)
        return self.output_dir


def main():
    import argparse

    parser = argparse.ArgumentParser(description="数字人汇报系统")
    parser.add_argument("ppt", help="PPT 文件路径")
    parser.add_argument("--style", default="formal",
                        choices=["formal", "casual", "training"],
                        help="演讲风格")
    parser.add_argument("--avatar", default=None,
                        help="头像图片路径（默认使用 templates/default_avatar.png）")
    parser.add_argument("--output", default=None, help="输出文件名")
    parser.add_argument("--skip-avatar", action="store_true",
                        help="跳过数字人视频生成，仅生成音频")
    parser.add_argument("--fallback", action="store_true",
                        help="使用 FFmpeg 静态图片模式（快速）")
    parser.add_argument("--cpu", action="store_true",
                        help="强制使用 CPU 模式（GPU 显存不足时使用）")
    parser.add_argument("--layout", default="pip",
                        choices=["pip", "avatar-only"],
                        help="视频布局 (pip=画中画, avatar-only=仅数字人)")
    parser.add_argument("--no-subtitles", action="store_true",
                        help="禁用字幕叠加")
    parser.add_argument("--no-bgm", action="store_true",
                        help="禁用背景音乐")
    parser.add_argument("--bgm-file", default=None,
                        help="自定义 BGM 文件路径")
    parser.add_argument("--rate", default="+0%",
                        help="语速调节 (如 +20%%, -10%%)")
    parser.add_argument("--language", default="auto",
                        choices=["auto", "zh", "en"],
                        help="演讲语言 (auto=自动检测, zh=中文, en=英文)")
    parser.add_argument("--emotion", default="default",
                        choices=list(EMOTION_PRESETS.keys()),
                        help="语音情感风格")
    parser.add_argument("--transition", default="fade",
                        choices=["fade", "wipeleft", "wipeup", "dissolve",
                                 "slidedown", "circlecrop", "radial",
                                 "smoothleft", "none"],
                        help="转场效果")
    parser.add_argument("--watermark", default=None,
                        help="文字水印内容")
    parser.add_argument("--watermark-image", default=None,
                        help="图片水印路径")
    parser.add_argument("--no-cover", action="store_true",
                        help="不生成封面图")

    args = parser.parse_args()

    pipeline = DigitalHumanPipeline()
    pipeline.run(
        ppt_path=args.ppt,
        style=args.style,
        avatar_image=args.avatar,
        output_name=args.output,
        skip_avatar=args.skip_avatar,
        use_cpu=args.cpu,
        use_fallback=args.fallback,
        layout=args.layout,
        subtitles=not args.no_subtitles,
        bgm=not args.no_bgm,
        bgm_path=args.bgm_file,
        rate=args.rate,
        language=args.language,
        emotion=args.emotion,
        transition=args.transition,
        watermark_text=args.watermark,
        watermark_image=args.watermark_image,
        generate_cover_image=not args.no_cover,
    )


if __name__ == "__main__":
    main()
