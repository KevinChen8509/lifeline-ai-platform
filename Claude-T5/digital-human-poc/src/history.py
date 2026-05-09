"""生成历史记录管理 - SQLite 存储"""

import json
import sqlite3
from datetime import datetime
from pathlib import Path

from src.logger import get_logger

log = get_logger(__name__)

DB_PATH = Path(__file__).parent.parent / "outputs" / "history.db"

_SCHEMA = """
CREATE TABLE IF NOT EXISTS records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    time TEXT NOT NULL,
    ppt TEXT NOT NULL,
    mode TEXT,
    style TEXT,
    voice TEXT,
    pages INTEGER DEFAULT 0,
    duration REAL DEFAULT 0,
    output_dir TEXT,
    video TEXT,
    audio TEXT,
    script TEXT
);
"""


def _get_conn() -> sqlite3.Connection:
    """获取数据库连接（自动建表）"""
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(DB_PATH))
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.executescript(_SCHEMA)
    return conn


def _migrate_json():
    """从旧 JSON 格式迁移到 SQLite（一次性）"""
    json_path = DB_PATH.parent / "history.json"
    if not json_path.exists():
        return
    try:
        old = json.loads(json_path.read_text(encoding="utf-8"))
        if not old:
            return
        conn = _get_conn()
        for r in old:
            conn.execute(
                "INSERT OR IGNORE INTO records (id,time,ppt,mode,style,voice,pages,duration,output_dir,video,audio,script) "
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                (r.get("id"), r.get("time"), r.get("ppt"), r.get("mode"),
                 r.get("style"), r.get("voice"), r.get("pages", 0),
                 r.get("duration", 0), r.get("output_dir"),
                 r.get("video"), r.get("audio"), r.get("script")),
            )
        conn.commit()
        conn.close()
        # 重命名旧文件
        json_path.rename(json_path.with_suffix(".json.bak"))
        log.info("迁移历史记录: %d 条 JSON -> SQLite", len(old))
    except Exception as e:
        log.warning("JSON 迁移跳过: %s", e)


def load_history() -> list[dict]:
    """加载历史记录"""
    _migrate_json()
    try:
        conn = _get_conn()
        rows = conn.execute("SELECT * FROM records ORDER BY id DESC LIMIT 100").fetchall()
        conn.close()
        return [dict(r) for r in rows]
    except Exception:
        return []


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
    record = {
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
    try:
        conn = _get_conn()
        conn.execute(
            "INSERT INTO records (time,ppt,mode,style,voice,pages,duration,output_dir,video,audio,script) "
            "VALUES (:time,:ppt,:mode,:style,:voice,:pages,:duration,:output_dir,:video,:audio,:script)",
            record,
        )
        conn.commit()
        record["id"] = conn.execute("SELECT last_insert_rowid()").fetchone()[0]
        conn.close()
    except Exception as e:
        log.warning("保存历史记录失败: %s", e)
    return record


def format_history_md() -> str:
    """将历史记录格式化为 Markdown"""
    records = load_history()
    if not records:
        return "暂无历史记录"

    lines = ["## 生成历史\n"]
    for r in records[:20]:  # 最近 20 条
        badge = "[预览]" if r.get("mode") == "预览" else ""
        lines.append(
            f"**{r['time']}** {badge} `{r['ppt']}` — "
            f"{r['pages']}页, {r['duration']}s\n"
        )
    return "\n".join(lines)
