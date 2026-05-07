"""快速测试入口 - 验证各模块是否正常工作"""

import sys
from pathlib import Path

# 确保项目根目录在 path 中
sys.path.insert(0, str(Path(__file__).parent))


def test_ppt_parser():
    """测试 PPT 解析（使用内置示例 PPT）"""
    print("\n=== 测试 PPT 解析器 ===")
    from src.parsers.ppt_parser import parse_ppt

    sample = Path(__file__).parent / "samples" / "demo.pptx"
    if not sample.exists():
        print(f"  跳过: 示例 PPT 不存在 ({sample})")
        print("  提示: 放一个 .pptx 文件到 samples/ 目录下即可测试")
        return

    slides = parse_ppt(sample)
    for s in slides:
        print(f"  第{s.slide_index + 1}页: {s.title or '(无标题)'} "
              f"[文本{len(s.body_texts)}段 图片{'有' if s.has_images else '无'}]")


def test_tts():
    """测试 TTS 语音合成"""
    print("\n=== 测试 TTS 语音合成 ===")
    from src.tts.engine import synthesize_speech

    demo_text = "大家好，这是数字人汇报系统的语音测试。如果你能听到这段话，说明TTS模块工作正常。"
    result = synthesize_speech(demo_text)
    print(f"  合成成功: {result.audio_path} (约 {result.duration_seconds:.1f}s)")


def test_llm():
    """测试 LLM 连接"""
    print("\n=== 测试 LLM 连接 ===")
    from src.config import LLM_API_KEY, LLM_MODEL, LLM_BASE_URL

    if not LLM_API_KEY:
        print("  跳过: 未配置 LLM_API_KEY")
        print("  提示: 在 .env 文件中设置 LLM_API_KEY")
        return

    from src.generators.script_generator import generate_script
    demo_md = "## 第1页\n### 项目概览\n本项目是一个数字人汇报系统。\n\n## 第2页\n### 技术方案\n采用PPT解析+LLM生成+TTS合成方案。"
    result = generate_script(demo_md, style="casual")
    print(f"  生成成功，长度: {len(result)} 字符")
    print(f"  预览: {result[:200]}...")


def test_avatar():
    """测试数字人驱动"""
    print("\n=== 测试数字人驱动 ===")
    from src.avatar.sadtalker_driver import check_sadtalker_installed

    if check_sadtalker_installed():
        print("  SadTalker 已安装，可使用面部驱动")
    else:
        print("  SadTalker 未安装，将使用 FFmpeg 静态模式作为降级方案")
        print("  安装 SadTalker: pip install sadtalker")


def main():
    print("数字人汇报系统 - 模块检测\n")

    tests = [
        ("PPT 解析", test_ppt_parser),
        ("TTS 语音合成", test_tts),
        ("LLM 连接", test_llm),
        ("数字人驱动", test_avatar),
    ]

    results = {}
    for name, test_fn in tests:
        try:
            test_fn()
            results[name] = "OK"
        except Exception as e:
            results[name] = f"FAIL: {e}"

    print("\n" + "=" * 40)
    print("检测结果汇总:")
    print("-" * 40)
    for name, status in results.items():
        icon = "✓" if status == "OK" else "✗"
        print(f"  {icon} {name}: {status}")
    print("=" * 40)


if __name__ == "__main__":
    main()
