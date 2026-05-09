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


def test_api_has_websocket_import():
    """测试 WebSocket 相关导入"""
    from api import _ws_broadcast, _ws_clients, _ws_lock
    assert isinstance(_ws_clients, dict)
    assert _ws_lock is not None


def test_api_avatar_endpoints():
    """测试头像端点导入"""
    from api import list_avatars
    result = list_avatars()
    assert "total" in result
    assert result["total"] > 0


def test_api_health_has_avatars():
    """测试健康检查包含头像数"""
    from api import health_check
    result = health_check()
    assert "avatars" in result
    assert result["avatars"] > 0


def test_api_options_has_backgrounds():
    """测试可选项包含虚拟背景"""
    from api import get_options
    result = get_options()
    assert "virtual_backgrounds" in result
    assert len(result["virtual_backgrounds"]) > 0


def test_api_rate_limit_import():
    """测试限流变量可导入"""
    from api import _RATE_LIMIT, rate_limit_middleware
    assert isinstance(_RATE_LIMIT, int)
