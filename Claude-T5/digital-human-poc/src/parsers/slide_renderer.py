"""PPT 幻灯片渲染 - 将 PPT 页面导出为图片"""

import os
import subprocess
from pathlib import Path


def render_slides(
    ppt_path: str | Path,
    output_dir: str | Path,
    width: int = 1280,
    height: int = 720,
) -> list[Path]:
    """
    将 PPT 每页导出为 PNG 图片

    使用 PowerPoint COM 自动化（pywin32）实现高质量渲染，
    完美还原 PPT 中的字体、动画、图表等效果。

    Args:
        ppt_path: PPT 文件路径
        output_dir: 输出目录
        width: 导出图片宽度
        height: 导出图片高度

    Returns:
        导出的 PNG 文件路径列表（按页码顺序）
    """
    ppt_path = Path(ppt_path).resolve()
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    if not ppt_path.exists():
        raise FileNotFoundError(f"PPT 文件不存在: {ppt_path}")

    # 使用 PowerPoint COM 导出
    import win32com.client

    ppt_app = None
    presentation = None
    try:
        # 启动 PowerPoint（不可见模式）
        ppt_app = win32com.client.Dispatch("PowerPoint.Application")

        # 打开演示文稿
        presentation = ppt_app.Presentations.Open(
            str(ppt_path),
            WithWindow=False,
        )

        slide_count = presentation.Slides.Count
        images = []

        for i in range(1, slide_count + 1):
            slide = presentation.Slides(i)
            out_path = output_dir / f"slide_{i - 1:03d}.png"

            # Export 方法: (路径, 过滤器名, 宽度, 高度)
            slide.Export(str(out_path.resolve()), "PNG", width, height)

            if out_path.exists():
                images.append(out_path)
                print(f"  导出第 {i} 页: {out_path.name}")
            else:
                print(f"  警告: 第 {i} 页导出失败")

        return images

    except ImportError:
        raise RuntimeError(
            "pywin32 未安装，无法使用 PowerPoint COM。\n"
            "请运行: pip install pywin32"
        )
    except Exception as e:
        if "PowerPoint" in str(e) or "Dispatch" in str(e):
            raise RuntimeError(
                f"无法启动 PowerPoint COM: {e}\n"
                "请确保已安装 Microsoft PowerPoint。"
            )
        raise
    finally:
        # 确保关闭 PowerPoint
        if presentation is not None:
            try:
                presentation.Close()
            except Exception:
                pass
        if ppt_app is not None:
            try:
                ppt_app.Quit()
            except Exception:
                pass
