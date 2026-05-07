"""生成历史记录管理"""

import json
from datetime import datetime
from pathlib import Path

HISTORY_FILE = Path(__file__).parent.parent / "outputs" / "history.json"


def load_history() -> list[dict]:
    """加载历史记录"""
    if not HISTORY_FILE.exists():
        return []
    try:
        return json.loads(HISTORY_FILE.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return []


def save_history(records: list[dict]):
    """保存历史记录"""
    HISTORY_FILE.parent.mkdir(parents=True, exist_ok=True)
    HISTORY_FILE.write_text(
        json.dumps(records, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def add_record(
    ppt_name: str,
    mode: str,
    style: str,
    voice: str,
    output_dir: str,
    video_path: str | None = None,
    audio_path: str | None = None,
    script_path: str | None = None,
    duration_seconds: float = 0,
    pages: int = 0,
    is_preview: bool = False,
) -> dict:
    """添加一条历史记录"""
    records = load_history()
    record = {
        "id": len(records) + 1,
        "time": datetime.now().isoformat(timespec="seconds"),
        "ppt": ppt_name,
        "mode": "预览" if is_preview else mode,
        "style": style,
        "voice": voice,
        "pages": pages,
        "duration": round(duration_seconds, 1),
        "output_dir": str(output_dir),
        "video": str(video_path) if video_path else None,
        "audio": str(audio_path) if audio_path else None,
        "script": str(script_path) if script_path else None,
    }
    records.append(record)
    # 只保留最近 100 条
    if len(records) > 100:
        records = records[-100:]
    save_history(records)
    return record


def format_history_md() -> str:
    """将历史记录格式化为 Markdown"""
    records = load_history()
    if not records:
        return "暂无历史记录"

    lines = ["## 生成历史\n"]
    for r in reversed(records[-20:]):  # 最近 20 条
        badge = "[预览]" if r.get("mode") == "预览" else ""
        lines.append(
            f"**{r['time']}** {badge} `{r['ppt']}` — "
            f"{r['pages']}页, {r['duration']}s\n"
        )
    return "\n".join(lines)
