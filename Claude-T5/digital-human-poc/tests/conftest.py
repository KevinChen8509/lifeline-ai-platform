"""conftest.py - 共享 fixtures"""

import sys
from pathlib import Path

# 确保项目根目录在 sys.path 中
sys.path.insert(0, str(Path(__file__).parent.parent))

import pytest

SAMPLES_DIR = Path(__file__).parent.parent / "samples"
SAMPLE_PPT = SAMPLES_DIR / "demo.pptx"


@pytest.fixture
def sample_ppt():
    """示例 PPT 文件路径"""
    if not SAMPLE_PPT.exists():
        pytest.skip("示例 PPT 不存在")
    return SAMPLE_PPT


@pytest.fixture
def tmp_output(tmp_path):
    """临时输出目录"""
    return tmp_path
