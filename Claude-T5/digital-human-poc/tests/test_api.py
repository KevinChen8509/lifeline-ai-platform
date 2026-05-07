"""测试 REST API"""

import pytest


def test_api_import():
    """测试 API 模块可导入"""
    from api import app, get_options
    assert app is not None


def test_get_options():
    """测试获取可选项"""
    from api import get_options
    result = get_options()
    assert "styles" in result
    assert "emotions" in result
    assert "transitions" in result
    assert "resolutions" in result
    assert "720p" in result["resolutions"]
    assert "formal" in result["styles"]


def test_list_jobs_empty():
    """测试空任务列表"""
    from api import list_jobs
    result = list_jobs()
    # 可能有残留任务，但应该返回列表
    assert hasattr(result, "jobs")


def test_job_not_found():
    """测试查询不存在的任务"""
    from api import get_job_status
    with pytest.raises(Exception):
        get_job_status("nonexistent_job_id")


def test_tts_cache_key():
    """测试缓存键生成"""
    from src.tts.engine import _cache_key
    k1 = _cache_key("hello", "voice1", "+0%", "+0Hz", "+0%")
    k2 = _cache_key("hello", "voice1", "+0%", "+0Hz", "+0%")
    k3 = _cache_key("world", "voice1", "+0%", "+0Hz", "+0%")
    assert k1 == k2
    assert k1 != k3


def test_clear_cache():
    """测试清空缓存不报错"""
    from src.tts.engine import clear_tts_cache
    clear_tts_cache()  # 应该不报错


def test_resolution_presets():
    """测试分辨率预设"""
    from src.avatar.compositor import RESOLUTION_PRESETS, _get_resolution
    assert RESOLUTION_PRESETS["720p"] == (1280, 720)
    assert RESOLUTION_PRESETS["1080p"] == (1920, 1080)
    assert RESOLUTION_PRESETS["4K"] == (3840, 2160)
    # 自定义格式
    assert _get_resolution("640x480") == (640, 480)
    # 无效格式回退到 720p
    assert _get_resolution("invalid") == (1280, 720)
