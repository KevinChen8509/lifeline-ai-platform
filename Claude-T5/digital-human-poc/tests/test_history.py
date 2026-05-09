"""测试历史记录模块（SQLite 版本）"""

from pathlib import Path


def test_add_and_load_history(tmp_path, monkeypatch):
    """测试添加和加载历史记录"""
    from src.history import add_record, load_history, DB_PATH

    db = tmp_path / "history.db"
    monkeypatch.setattr("src.history.DB_PATH", db)

    record = add_record(
        ppt_name="test.pptx",
        mode="fallback",
        style="formal",
        voice="云希",
        output_dir=str(tmp_path),
        duration_seconds=42.0,
        pages=5,
    )

    assert record["ppt"] == "test.pptx"
    assert record["pages"] == 5

    records = load_history()
    assert len(records) == 1
    assert records[0]["ppt"] == "test.pptx"


def test_format_history_md(tmp_path, monkeypatch):
    """测试历史 Markdown 格式化"""
    from src.history import add_record, format_history_md

    db = tmp_path / "history.db"
    monkeypatch.setattr("src.history.DB_PATH", db)

    # 空历史
    md = format_history_md()
    assert "暂无" in md

    # 添加记录
    add_record(
        ppt_name="demo.pptx", mode="fallback", style="formal",
        voice="云希", output_dir=str(tmp_path), pages=3,
    )
    md = format_history_md()
    assert "demo.pptx" in md


def test_history_max_100(tmp_path, monkeypatch):
    """测试历史记录最多保留 100 条"""
    from src.history import add_record, load_history

    db = tmp_path / "history.db"
    monkeypatch.setattr("src.history.DB_PATH", db)

    for i in range(105):
        add_record(
            ppt_name=f"test_{i}.pptx", mode="fallback", style="formal",
            voice="云希", output_dir=str(tmp_path), pages=1,
        )

    records = load_history()
    assert len(records) == 100
