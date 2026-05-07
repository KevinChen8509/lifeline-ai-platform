"""
数字人汇报系统 - 快速演示

用法:
    python demo.py                        # 完整流程 (PPT→演讲稿→TTS→画中画视频)
    python demo.py --fallback             # 使用 FFmpeg 静态图片模式 (快速)
    python demo.py --skip-avatar          # 仅生成音频，跳过视频
    python demo.py --layout avatar-only   # 仅数字人，不含 PPT 画面
    python demo.py --ppt path/to/ppt.pptx # 指定 PPT 文件
    python demo.py --batch dir/           # 批量处理目录下所有 .pptx 文件
"""

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from src.pipeline import DigitalHumanPipeline


def main():
    parser = argparse.ArgumentParser(description="数字人汇报系统 Demo")
    parser.add_argument("--ppt", default="samples/demo.pptx", help="PPT 文件路径")
    parser.add_argument("--style", default="formal",
                        choices=["formal", "casual", "training"])
    parser.add_argument("--fallback", action="store_true",
                        help="使用 FFmpeg 静态图片模式（快速，无面部动画）")
    parser.add_argument("--skip-avatar", action="store_true",
                        help="仅生成音频")
    parser.add_argument("--avatar", default=None, help="头像图片路径")
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
                        choices=["default", "cheerful", "serious", "gentle", "energetic", "calm"],
                        help="语音情感风格")
    parser.add_argument("--batch", default=None,
                        help="批量处理: 指定目录，处理其中所有 .pptx 文件")
    args = parser.parse_args()

    # 批量模式
    if args.batch:
        batch_dir = Path(args.batch)
        if not batch_dir.is_dir():
            print(f"错误: 目录不存在: {batch_dir}")
            sys.exit(1)
        ppt_files = sorted(batch_dir.glob("*.pptx"))
        if not ppt_files:
            print(f"错误: 目录中没有 .pptx 文件: {batch_dir}")
            sys.exit(1)
        print(f"批量处理: 发现 {len(ppt_files)} 个 PPT 文件")
        for i, ppt in enumerate(ppt_files, 1):
            print(f"\n{'='*50}")
            print(f"[{i}/{len(ppt_files)}] {ppt.name}")
            print('='*50)
            pipeline = DigitalHumanPipeline()
            pipeline.run(
                ppt_path=ppt,
                style=args.style,
                avatar_image=args.avatar,
                use_fallback=args.fallback,
                skip_avatar=args.skip_avatar,
                layout=args.layout,
                subtitles=not args.no_subtitles,
                bgm=not args.no_bgm,
                bgm_path=args.bgm_file,
                rate=args.rate,
                language=args.language,
                emotion=args.emotion,
            )
        return

    # 单文件模式

    ppt = Path(args.ppt)
    if not ppt.exists():
        print(f"错误: PPT 文件不存在: {ppt}")
        sys.exit(1)

    pipeline = DigitalHumanPipeline()
    pipeline.run(
        ppt_path=ppt,
        style=args.style,
        avatar_image=args.avatar,
        use_fallback=args.fallback,
        skip_avatar=args.skip_avatar,
        layout=args.layout,
        subtitles=not args.no_subtitles,
        bgm=not args.no_bgm,
        bgm_path=args.bgm_file,
        rate=args.rate,
    )


if __name__ == "__main__":
    main()
