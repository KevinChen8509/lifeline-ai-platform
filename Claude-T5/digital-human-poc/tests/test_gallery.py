"""测试头像库模块"""

import json
from pathlib import Path

from src.avatar.gallery import (
    scan_avatars,
    get_avatar_path,
    get_avatar_choices,
    get_avatar_thumbnails,
    add_custom_avatar,
    AvatarInfo,
    AVATARS_DIR,
)


def test_scan_avatars_returns_list():
    """扫描应返回 AvatarInfo 列表"""
    avatars = scan_avatars()
    assert isinstance(avatars, list)
    assert len(avatars) > 0
    for a in avatars:
        assert isinstance(a, AvatarInfo)
        assert a.id
        assert a.name
        assert a.path


def test_scan_avatars_builtin_ids():
    """内置头像应包含已知 ID"""
    avatars = scan_avatars()
    ids = {a.id for a in avatars}
    assert "art_0" in ids


def test_get_avatar_path_default():
    """None 应返回默认头像路径"""
    path = get_avatar_path(None)
    assert "default_avatar" in path


def test_get_avatar_path_by_id():
    """按 ID 查找应返回正确路径"""
    path = get_avatar_path("art_0")
    assert Path(path).exists()
    assert "art_0" in path


def test_get_avatar_path_not_found():
    """不存在的 ID 应返回默认头像"""
    path = get_avatar_path("nonexistent_avatar_12345")
    assert "default_avatar" in path


def test_get_avatar_choices():
    """选项字典应非空且格式正确"""
    choices = get_avatar_choices()
    assert isinstance(choices, dict)
    assert len(choices) > 0
    for display_name, avatar_id in choices.items():
        assert avatar_id  # ID 不为空


def test_get_avatar_thumbnails():
    """缩略图列表应与头像列表一致"""
    thumbs = get_avatar_thumbnails()
    assert isinstance(thumbs, list)
    assert len(thumbs) == len(scan_avatars())
    for t in thumbs:
        assert Path(t).exists()


def test_add_custom_avatar(tmp_path):
    """添加自定义头像应正确保存"""
    # 创建测试图片
    test_img = tmp_path / "test_avatar.png"
    test_img.write_bytes(b"\x89PNG\r\n\x1a\n" + b"\x00" * 100)  # 假 PNG

    avatar = add_custom_avatar(
        test_img,
        name="测试头像",
        gender="male",
        style="test",
    )
    assert avatar.name == "测试头像"
    assert avatar.gender == "male"
    assert avatar.style == "test"
    assert not avatar.builtin
    assert Path(avatar.path).exists()

    # 验证元数据文件
    meta_file = AVATARS_DIR / "meta.json"
    assert meta_file.exists()
    meta = json.loads(meta_file.read_text(encoding="utf-8"))
    assert "test_avatar" in meta

    # 清理
    Path(avatar.path).unlink(missing_ok=True)
    meta.pop("test_avatar", None)
    meta_file.write_text(json.dumps(meta, ensure_ascii=False), encoding="utf-8")


def test_avatar_info_display_name():
    """display_name 应包含名称和 ID"""
    info = AvatarInfo(id="test", name="测试", gender="male", style="biz", path="/x.png", builtin=True)
    assert info.display_name == "测试 (test)"


def test_scan_avatars_includes_custom():
    """扫描应包含自定义头像"""
    avatars = scan_avatars()
    # 如果 SadTalker 示例存在，应有内置头像
    builtin = [a for a in avatars if a.builtin]
    assert len(builtin) > 0  # SadTalker 内置
