"""FastAPI REST API — 前端可视化界面的后端服务。"""

from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import Any

from typing import Literal

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from text2sql.config import AppConfig, DatabaseConfig, LLMConfig, SecurityConfig
from text2sql.feedback import Feedback, FeedbackManager, FeedbackType
from text2sql.formatter import FormatType, ResultFormatter
from text2sql.knowledge.chromadb import ChromaDBStore
from text2sql.llm.service import LLMService
from text2sql.orchestrator import QueryOrchestrator
from text2sql.conversation import ConversationManager

logger = logging.getLogger(__name__)

# ── 全局状态 ──────────────────────────────────────────────

_skill: Text2SQLSkill | None = None
_feedback_mgr: FeedbackManager | None = None
_conv_mgr: ConversationManager | None = None


def _get_skill() -> Text2SQLSkill:
    global _skill, _feedback_mgr, _conv_mgr
    if _skill is not None:
        return _skill

    from text2sql.interface.skill import Text2SQLSkill

    # TEXT2SQL_DEMO=1 强制使用 SQLite 演示模式
    use_demo = os.environ.get("TEXT2SQL_DEMO", "").strip().lower() in ("1", "true", "yes")

    config_path = Path("config.yaml")
    if use_demo or not config_path.exists():
        _skill = _create_demo_skill()
    else:
        _skill = Text2SQLSkill.from_config(str(config_path))

    _feedback_mgr = FeedbackManager(_skill.knowledge)
    _conv_mgr = ConversationManager(auto_learn=_skill.config.security.auto_learn)
    return _skill


def _create_demo_skill() -> "Text2SQLSkill":
    """创建演示模式：SQLite + PostgreSQL（如果可用）。"""
    from text2sql.interface.skill import Text2SQLSkill
    from text2sql.schema.sqlite import SQLiteSchemaReader
    from text2sql.runner.sqlite import SQLiteSqlRunner

    db_path = os.environ.get("TEXT2SQL_DB_PATH", "./data/test_e2e.db")

    # 如果数据库不存在，创建演示数据
    if not Path(db_path).exists():
        from scripts.e2e_test import setup_test_db
        Path(db_path).parent.mkdir(parents=True, exist_ok=True)
        setup_test_db(db_path)

    databases = [
        DatabaseConfig(
            name="test_sqlite",
            type="sqlite",
            host="localhost",
            port=0,
            username="default",
            password="",
            database=db_path,
        ),
    ]

    # 检测 PostgreSQL 是否可用
    pg_host = os.environ.get("PG_HOST", "localhost")
    pg_port = int(os.environ.get("PG_PORT", "5432"))
    pg_user = os.environ.get("PG_USER", "postgres")
    pg_pass = os.environ.get("PG_PASSWORD", "postgres")
    pg_db = os.environ.get("PG_DATABASE", "text2sql")

    pg_available = False
    try:
        import psycopg2
        conn = psycopg2.connect(host=pg_host, port=pg_port, user=pg_user, password=pg_pass, dbname=pg_db, connect_timeout=3)
        conn.close()
        pg_available = True
    except Exception:
        pass

    if pg_available:
        databases.append(
            DatabaseConfig(
                name="pg_business",
                type="postgresql",
                host=pg_host,
                port=pg_port,
                username=pg_user,
                password=pg_pass,
                database=pg_db,
            ),
        )
        # drain schema（排水管网数据）
        databases.append(
            DatabaseConfig(
                name="pg_drain",
                type="postgresql",
                host=pg_host,
                port=pg_port,
                username=pg_user,
                password=pg_pass,
                database=pg_db,
                schema_="drain",
            ),
        )

    api_key = os.environ.get("LLM_API_KEY", "")
    config = AppConfig(
        llm=LLMConfig(
            base_url=os.environ.get("LLM_BASE_URL", "https://ark.cn-beijing.volces.com/api/v3"),
            api_key=api_key,
            model=os.environ.get("LLM_MODEL", "ep-20260415090611-nhwrx"),
            temperature=0.1,
            timeout=120,
        ),
        databases=databases,
        security=SecurityConfig(mode="readonly"),
    )

    knowledge = ChromaDBStore(persist_dir="./data/knowledge")
    llm = LLMService(config.llm) if api_key else None
    orch = QueryOrchestrator(config=config, knowledge=knowledge, llm_service=llm)

    # 注入 SQLite 适配器
    orch._readers["test_sqlite"] = SQLiteSchemaReader(db_path, db_name="test_sqlite")
    orch._runners["test_sqlite"] = SQLiteSqlRunner(db_path, db_name="test_sqlite")

    skill = Text2SQLSkill(config=config, knowledge=knowledge, orchestrator=orch)
    return skill


# ── 请求/响应模型 ─────────────────────────────────────────

class AskRequest(BaseModel):
    question: str = Field(..., min_length=1, description="自然语言问题")
    db_name: str | None = Field(None, description="指定数据库")
    fmt: str = Field("table", description="输出格式: table/markdown/csv/json")


class TrainRequest(BaseModel):
    ddl: str | None = None
    documentation: str | None = None
    question: str | None = None
    sql: str | None = None
    db: str | None = None


class FeedbackRequest(BaseModel):
    question: str
    sql: str
    feedback_type: str = Field(..., description="positive/negative/correction")
    corrected_sql: str = ""
    db_name: str = ""


class ConversationRequest(BaseModel):
    db_name: str = ""
    db_type: str = ""


# ── FastAPI 应用 ──────────────────────────────────────────

app = FastAPI(
    title="Text-to-SQL Skill API",
    version="0.1.0",
    description="自然语言查询数据库 REST API",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
def startup():
    _get_skill()
    logger.info("Text-to-SQL Skill API 已启动")


# ── API 端点 ──────────────────────────────────────────────

@app.get("/api/status")
def get_status() -> dict[str, Any]:
    """获取系统状态。"""
    skill = _get_skill()
    return skill.status()


@app.post("/api/ask")
def ask(req: AskRequest) -> dict[str, Any]:
    """自然语言查询。"""
    from text2sql.orchestrator.models import AskStatus
    from text2sql.formatter import FormatType

    skill = _get_skill()
    try:
        ask_result = skill.orchestrator.ask(req.question, db_name=req.db_name)

        output: dict[str, Any] = {
            "question": req.question,
            "sql": ask_result.sql,
            "status": ask_result.status.value,
            "db_name": ask_result.db_name,
            "db_type": ask_result.db_type,
            "elapsed_ms": round(ask_result.elapsed_ms, 1),
            "error": ask_result.error or None,
        }

        if ask_result.success and ask_result.query_result:
            qr = ask_result.query_result
            df = qr.data
            output["data"] = df.to_dict(orient="records") if hasattr(df, "to_dict") and len(df) > 0 else []
            output["row_count"] = qr.row_count
            output["truncated"] = qr.truncated

            chart_rec = skill.formatter.recommend_chart(qr)
            output["chart_recommendation"] = {
                "chart_type": chart_rec.chart_type.value,
                "x_column": chart_rec.x_column,
                "y_column": chart_rec.y_column,
                "color_column": chart_rec.color_column or "",
                "reason": chart_rec.reason,
            }
        else:
            output["data"] = []
            output["row_count"] = 0

        if ask_result.autofix and ask_result.autofix.was_fixed:
            output["autofixed"] = True
            output["autofix_attempts"] = ask_result.autofix.total_fixes

        return output
    except Exception as e:
        logger.error("ask 失败: %s", e)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/train")
def train(req: TrainRequest) -> dict[str, Any]:
    """手动训练知识库。"""
    skill = _get_skill()
    return skill.train(
        ddl=req.ddl,
        documentation=req.documentation,
        question=req.question,
        sql=req.sql,
        db=req.db,
    )


@app.post("/api/train/auto")
def train_auto() -> dict[str, Any]:
    """自动采集所有数据库 Schema。"""
    skill = _get_skill()
    return skill.train(auto=True)


@app.post("/api/feedback")
def submit_feedback(req: FeedbackRequest) -> dict[str, Any]:
    """提交用户反馈。"""
    global _feedback_mgr
    skill = _get_skill()
    if _feedback_mgr is None:
        _feedback_mgr = FeedbackManager(skill.knowledge)

    try:
        fb_type = FeedbackType(req.feedback_type)
    except ValueError:
        raise HTTPException(status_code=400, detail=f"无效反馈类型: {req.feedback_type}")

    feedback = Feedback(
        question=req.question,
        sql=req.sql,
        feedback_type=fb_type,
        corrected_sql=req.corrected_sql,
        db_name=req.db_name,
    )
    result = _feedback_mgr.submit_feedback(feedback)
    return {"accepted": result.accepted, "action": result.action, "reason": result.reason}


@app.post("/api/conversation")
def create_conversation(req: ConversationRequest) -> dict[str, Any]:
    """创建新的对话会话。"""
    global _conv_mgr
    _get_skill()
    if _conv_mgr is None:
        _conv_mgr = ConversationManager()
    conv = _conv_mgr.create_conversation(db_name=req.db_name, db_type=req.db_type)
    return {"conversation_id": conv.id, "db_name": conv.db_name}


@app.get("/api/conversation/{conv_id}")
def get_conversation(conv_id: str) -> dict[str, Any]:
    """获取对话历史。"""
    global _conv_mgr
    _get_skill()
    if _conv_mgr is None:
        _conv_mgr = ConversationManager()
    conv = _conv_mgr.get_conversation(conv_id)
    if not conv:
        raise HTTPException(status_code=404, detail="会话不存在")
    return {
        "id": conv.id,
        "db_name": conv.db_name,
        "turns": [
            {"question": t.question, "sql": t.sql, "answer": t.answer}
            for t in conv.turns
        ],
    }


@app.get("/api/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.delete("/api/knowledge/{collection_type}")
def clear_knowledge(collection_type: str) -> dict[str, Any]:
    """清空指定类型的知识库。collection_type: ddl / documentation / question_sql / all"""
    valid_types = ("ddl", "documentation", "question_sql", "all")
    if collection_type not in valid_types:
        raise HTTPException(status_code=400, detail=f"无效类型: {collection_type}，可选: {valid_types}")
    skill = _get_skill()
    deleted = skill.knowledge.clear_collection(collection_type)
    return {"status": "ok", "deleted": deleted}


@app.post("/api/knowledge/retrain")
def retrain_knowledge() -> dict[str, Any]:
    """清空全部知识库并重新自动训练。"""
    skill = _get_skill()
    cleared = skill.knowledge.clear_collection("all")
    result = skill.orchestrator.auto_train_schema()
    return {"status": "ok", "cleared": cleared, "train_result": result}


class DBConfigRequest(BaseModel):
    name: str = Field(..., min_length=1, description="数据库标识名称")
    type: Literal["sqlite", "postgresql", "clickhouse"] = Field(description="数据库类型")
    host: str = "localhost"
    port: int = 0
    username: str = ""
    password: str = ""
    database: str = ""
    schema_: str = Field(default="public", alias="schema", description="数据库 schema")
    readonly: bool = True
    max_rows: int = 10000
    timeout: int = 30
    secure: bool = False
    ssl_mode: str = "prefer"

    model_config = {"protected_namespaces": (), "populate_by_name": True}


def _test_db_connection(db_config: DatabaseConfig) -> tuple[bool, int, str]:
    """测试数据库连接，返回 (connected, tables_count, error_msg)。"""
    from text2sql.orchestrator import _create_schema_reader
    try:
        reader = _create_schema_reader(db_config)
        tables = reader.read_tables()
        return True, len(tables), ""
    except Exception as e:
        return False, 0, str(e)


def _register_db(db_config: DatabaseConfig) -> None:
    """将数据库注册到 orchestrator（reader + runner）。"""
    from text2sql.orchestrator import _create_schema_reader, _create_runner
    skill = _get_skill()
    orch = skill.orchestrator

    # 清除旧的缓存（如果同名更新）
    orch._readers.pop(db_config.name, None)
    orch._runners.pop(db_config.name, None)

    # 创建并注册新的 reader/runner
    orch._readers[db_config.name] = _create_schema_reader(db_config)
    orch._runners[db_config.name] = _create_runner(db_config)


@app.post("/api/db/test")
def test_db_config(req: DBConfigRequest) -> dict[str, Any]:
    """测试数据库连接（不保存）。"""
    db_config = DatabaseConfig(
        name=req.name, type=req.type, host=req.host, port=req.port,
        username=req.username, password=req.password, database=req.database,
        schema_=req.schema_, readonly=req.readonly, max_rows=req.max_rows,
        timeout=req.timeout, secure=req.secure, ssl_mode=req.ssl_mode,
    )
    connected, tables_count, error = _test_db_connection(db_config)
    return {"connected": connected, "tables_count": tables_count, "error": error or None}


@app.post("/api/db/config")
def add_db_config(req: DBConfigRequest) -> dict[str, Any]:
    """添加或更新数据库连接，自动测试并注册。"""
    skill = _get_skill()

    # 查找旧配置（更新场景保留密码）
    old_config = next((d for d in skill.config.databases if d.name == req.name), None)
    password = req.password if req.password else (old_config.password if old_config else "")

    db_config = DatabaseConfig(
        name=req.name, type=req.type, host=req.host, port=req.port,
        username=req.username, password=password, database=req.database,
        schema_=req.schema_, readonly=req.readonly, max_rows=req.max_rows,
        timeout=req.timeout, secure=req.secure, ssl_mode=req.ssl_mode,
    )

    # 测试连接
    connected, tables_count, error = _test_db_connection(db_config)
    if not connected:
        return {
            "status": "connection_failed",
            "db_name": req.name,
            "connected": False,
            "tables_count": 0,
            "error": f"连接失败: {error}",
        }

    # 移除同名旧配置（更新场景）
    skill.config.databases = [d for d in skill.config.databases if d.name != req.name]
    skill.config.databases.append(db_config)

    # 注册 reader/runner
    _register_db(db_config)

    logger.info("数据库已注册: %s (%s) %s", req.name, req.type, req.host)
    return {
        "status": "ok",
        "db_name": req.name,
        "connected": True,
        "tables_count": tables_count,
        "error": None,
    }


@app.delete("/api/db/config/{name}")
def remove_db_config(name: str) -> dict[str, Any]:
    """删除数据库连接。"""
    skill = _get_skill()
    before = len(skill.config.databases)
    skill.config.databases = [d for d in skill.config.databases if d.name != name]

    if len(skill.config.databases) == before:
        raise HTTPException(status_code=404, detail=f"数据库不存在: {name}")

    # 清除 reader/runner 缓存
    skill.orchestrator._readers.pop(name, None)
    skill.orchestrator._runners.pop(name, None)

    logger.info("数据库已移除: %s", name)
    return {"status": "ok", "removed": name}


class LLMConfigRequest(BaseModel):
    base_url: str | None = None
    api_key: str | None = None
    model: str | None = None


@app.post("/api/llm/config")
def update_llm_config(req: LLMConfigRequest) -> dict[str, Any]:
    """更新 LLM 配置，立即生效。"""
    skill = _get_skill()
    config = skill.config.llm

    if req.base_url is not None:
        config.base_url = req.base_url
    if req.api_key is not None:
        config.api_key = req.api_key
    if req.model is not None:
        config.model = req.model

    # 重置 LLM 客户端，下次调用时用新配置重建
    if skill.orchestrator.llm:
        skill.orchestrator.llm._client = None

    logger.info("LLM 配置已更新: model=%s, base_url=%s", config.model, config.base_url)
    return {
        "status": "ok",
        "llm": {
            "model": config.model,
            "base_url": config.base_url,
            "api_key": config.api_key[:8] + "..." if len(config.api_key) > 8 else "***",
        },
    }


@app.get("/api/tables")
def list_tables(db_name: str | None = None) -> dict[str, Any]:
    """获取数据库表列表。"""
    skill = _get_skill()
    orch = skill.orchestrator
    target = db_name or (skill.config.databases[0].name if skill.config.databases else "")
    if not target:
        return {"tables": []}
    try:
        reader = orch._get_reader(target)
        tables = reader.read_tables()
        result = []
        for t in tables:
            cols = reader.read_columns(t.name)
            result.append({
                "name": t.name,
                "comment": t.comment,
                "columns": [
                    {
                        "name": c.name,
                        "type": c.data_type,
                        "nullable": c.nullable,
                        "is_primary_key": c.is_primary_key,
                    }
                    for c in cols
                ],
                "column_count": len(cols),
            })
        return {"db_name": target, "tables": result}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/tables/{table_name}/sample")
def table_sample(table_name: str, db_name: str | None = None, n: int = 5) -> dict[str, Any]:
    """获取表样本数据。"""
    skill = _get_skill()
    orch = skill.orchestrator
    target = db_name or (skill.config.databases[0].name if skill.config.databases else "")
    if not target:
        return {"data": []}
    try:
        reader = orch._get_reader(target)
        df = reader.read_sample_data(table_name, n)
        return {
            "table": table_name,
            "rows": df.to_dict(orient="records") if len(df) > 0 else [],
            "columns": list(df.columns) if len(df) > 0 else [],
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ── 入口 ──────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
