"""数字人头像库 - 多头像管理与切换"""

import json
from pathlib import Path
from dataclasses import dataclass, asdict

from src.config import TEMPLATES_DIR

AVATARS_DIR = TEMPLATES_DIR / "avatars"
AVATARS_DIR.mkdir(parents=True, exist_ok=True)

# 内置头像元数据（SadTalker 示例图片）
# key = 文件名（不含路径）, desc = 中文描述
_BUILTIN_AVATARS = {
    "art_0.png": {"name": "商务男", "gender": "male", "style": "business"},
    "art_1.png": {"name": "知性女", "gender": "female", "style": "professional"},
    "art_2.png": {"name": "活力男", "gender": "male", "style": "casual"},
    "art_3.png": {"name": "温柔女", "gender": "female", "style": "gentle"},
    "art_4.png": {"name": "沉稳男", "gender": "male", "style": "formal"},
    "art_5.png": {"name": "清秀女", "gender": "female", "style": "fresh"},
    "art_6.png": {"name": "阳光男", "gender": "male", "style": "energetic"},
    "art_7.png": {"name": "优雅女", "gender": "female", "style": "elegant"},
    "full_body_1.png": {"name": "全身-男", "gender": "male", "style": "full-body"},
    "full_body_2.png": {"name": "全身-女", "gender": "female", "style": "full-body"},
}

# SadTalker 示例图片的实际路径
SADTALKER_EXAMPLES = Path(__file__).resolve().parent.parent.parent / "SadTalker" / "examples" / "source_image"


@dataclass
class AvatarInfo:
    """头像信息"""
    id: str            # 唯一标识（文件名去后缀）
    name: str          # 显示名称
    gender: str        # male / female
    style: str         # 风格标签
    path: str          # 图片文件路径
    builtin: bool      # 是否为内置头像

    @property
    def display_name(self) -> str:
        return f"{self.name} ({self.id})"


def scan_avatars() -> list[AvatarInfo]:
    """
    扫描所有可用头像

    优先级:
    1. templates/avatars/ 目录下的自定义头像
    2. SadTalker 内置示例头像
    """
    avatars = []
    seen_ids = set()

    # 1. 扫描自定义头像目录
    meta_file = AVATARS_DIR / "meta.json"
    custom_meta = {}
    if meta_file.exists():
        try:
            custom_meta = json.loads(meta_file.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            pass

    for img in sorted(AVATARS_DIR.glob("*")):
        if img.suffix.lower() not in (".png", ".jpg", ".jpeg", ".webp"):
            continue
        avatar_id = img.stem
        meta = custom_meta.get(avatar_id, {})
        avatars.append(AvatarInfo(
            id=avatar_id,
            name=meta.get("name", avatar_id),
            gender=meta.get("gender", "unknown"),
            style=meta.get("style", "custom"),
            path=str(img),
            builtin=False,
        ))
        seen_ids.add(avatar_id)

    # 2. 扫描 SadTalker 内置头像
    if SADTALKER_EXAMPLES.exists():
        for filename, meta in _BUILTIN_AVATARS.items():
            src = SADTALKER_EXAMPLES / filename
            if not src.exists():
                continue
            avatar_id = src.stem
            if avatar_id in seen_ids:
                continue  # 自定义头像优先
            avatars.append(AvatarInfo(
                id=avatar_id,
                name=meta["name"],
                gender=meta["gender"],
                style=meta["style"],
                path=str(src),
                builtin=True,
            ))
            seen_ids.add(avatar_id)

    return avatars


def get_avatar_path(avatar_id: str | None = None) -> str:
    """
    根据 ID 获取头像图片路径

    Args:
        avatar_id: 头像 ID（文件名去后缀），None 返回默认头像

    Returns:
        头像图片路径字符串
    """
    if avatar_id is None:
        return str(TEMPLATES_DIR / "default_avatar.png")

    # 先在自定义目录找
    for ext in (".png", ".jpg", ".jpeg", ".webp"):
        p = AVATARS_DIR / f"{avatar_id}{ext}"
        if p.exists():
            return str(p)

    # 再在 SadTalker 内置找
    if SADTALKER_EXAMPLES.exists():
        for ext in (".png", ".jpg", ".jpeg", ".webp"):
            p = SADTALKER_EXAMPLES / f"{avatar_id}{ext}"
            if p.exists():
                return str(p)

    # 兜底
    return str(TEMPLATES_DIR / "default_avatar.png")


def get_avatar_choices() -> dict[str, str]:
    """获取 UI 用的头像选项 {显示名: ID}"""
    avatars = scan_avatars()
    choices = {}
    for a in avatars:
        choices[a.display_name] = a.id
    return choices


def get_avatar_thumbnails() -> list[str]:
    """获取所有头像缩略图路径（用于 Gallery 组件）"""
    return [a.path for a in scan_avatars()]


def add_custom_avatar(image_path: str | Path, name: str = "",
                      gender: str = "unknown", style: str = "custom") -> AvatarInfo:
    """
    添加自定义头像到头像库

    Args:
        image_path: 源图片路径
        name: 显示名称
        gender: 性别
        style: 风格

    Returns:
        新添加的头像信息
    """
    import shutil
    src = Path(image_path)
    avatar_id = src.stem
    dest = AVATARS_DIR / src.name
    shutil.copy2(src, dest)

    # 更新元数据
    meta_file = AVATARS_DIR / "meta.json"
    meta = {}
    if meta_file.exists():
        try:
            meta = json.loads(meta_file.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            pass

    meta[avatar_id] = {"name": name or avatar_id, "gender": gender, "style": style}
    meta_file.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")

    return AvatarInfo(
        id=avatar_id,
        name=name or avatar_id,
        gender=gender,
        style=style,
        path=str(dest),
        builtin=False,
    )
