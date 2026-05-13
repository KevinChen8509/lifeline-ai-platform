"""配置模型 — Pydantic v2 + 环境变量解析"""

from __future__ import annotations

import os
import re
from pathlib import Path
from typing import Literal

import yaml
from pydantic import BaseModel, Field, field_validator


# ── 环境变量解析 ──────────────────────────────────────────

_ENV_PATTERN = re.compile(r"\$\{([^}]+)\}")


def _resolve_env(value: str) -> str:
    """解析 ${VAR} 格式的环境变量引用，未设置时保留原值。"""
    def _replace(match: re.Match) -> str:
        var_name = match.group(1)
        return os.environ.get(var_name, match.group(0))
    return _ENV_PATTERN.sub(_replace, value)


def _deep_resolve(obj):
    """递归解析 dict/list 中所有字符串的环境变量引用。"""
    if isinstance(obj, str):
        return _resolve_env(obj)
    if isinstance(obj, dict):
        return {k: _deep_resolve(v) for k, v in obj.items()}
    if isinstance(obj, list):
        return [_deep_resolve(item) for item in obj]
    return obj


# ── 数据库配置 ────────────────────────────────────────────

class DatabaseConfig(BaseModel):
    """单个数据库连接配置。"""

    model_config = {"protected_namespaces": (), "populate_by_name": True}

    name: str = Field(description="数据库标识名称")
    type: Literal["clickhouse", "postgresql", "sqlite"] = Field(description="数据库类型")
    host: str
    port: int
    username: str
    password: str = ""
    database: str
    schema_: str = Field(default="public", alias="schema", description="数据库 schema")
    readonly: bool = True
    max_rows: int = 10000
    timeout: int = 30

    # ClickHouse 专用
    secure: bool = False

    # PostgreSQL 专用
    ssl_mode: str = "prefer"


# ── LLM 配置 ─────────────────────────────────────────────

class LLMConfig(BaseModel):
    """LLM 服务配置 — OpenAI 兼容格式，支持国产大模型。"""

    base_url: str = Field(
        default="https://api.openai.com/v1",
        description="API 地址，可切换为火山引擎/智谱/DeepSeek 等",
    )
    api_key: str = Field(default="", description="API 密钥")
    model: str = Field(default="gpt-4o", description="模型名称或 endpoint ID")
    temperature: float = Field(default=0.1, ge=0.0, le=2.0)
    max_tokens: int = Field(default=2048, ge=1)
    timeout: int = Field(default=30, ge=1)


# ── 嵌入模型配置 ──────────────────────────────────────────

class EmbeddingConfig(BaseModel):
    """向量嵌入模型配置。"""

    model: str = Field(default="text2vec-base-chinese")
    base_url: str | None = Field(default=None, description="API 模式时的地址")
    api_key: str | None = Field(default=None, description="API 模式时的密钥")
    dimension: int = Field(default=768, ge=1)


# ── 知识库配置 ────────────────────────────────────────────

class KnowledgeConfig(BaseModel):
    """知识库存储配置。"""

    backend: Literal["chromadb", "pgvector"] = Field(default="chromadb")
    persist_dir: str = Field(default="./data/knowledge")
    top_k: int = Field(default=3, ge=1, le=50)
    similarity_threshold: float = Field(default=0.5, ge=0.0, le=1.0)


# ── 安全配置 ─────────────────────────────────────────────

class SecurityConfig(BaseModel):
    """安全与审计配置。"""

    mode: Literal["readonly", "readwrite"] = Field(default="readonly")
    max_execution_time: int = Field(default=30, ge=1, description="SQL 执行超时（秒）")
    max_result_rows: int = Field(default=1000, ge=1)
    blocked_keywords: list[str] = Field(
        default_factory=lambda: [
            "DROP", "DELETE", "TRUNCATE", "ALTER",
            "INSERT", "UPDATE", "GRANT", "REVOKE",
        ],
    )
    enable_row_filter: bool = False
    audit_log: str = Field(default="./data/audit/audit.jsonl")
    auto_learn: bool = Field(default=True, description="成功查询自动学习")


# ── 顶层配置 ─────────────────────────────────────────────

class AppConfig(BaseModel):
    """Text-to-SQL Skill 顶层配置。"""

    llm: LLMConfig = Field(default_factory=LLMConfig)
    embedding: EmbeddingConfig = Field(default_factory=EmbeddingConfig)
    knowledge: KnowledgeConfig = Field(default_factory=KnowledgeConfig)
    databases: list[DatabaseConfig] = Field(default_factory=list)
    security: SecurityConfig = Field(default_factory=SecurityConfig)

    @field_validator("databases", mode="before")
    @classmethod
    def validate_unique_names(cls, v: list) -> list:
        names = []
        for db in v:
            if isinstance(db, dict):
                names.append(db.get("name"))
            else:
                names.append(db.name if hasattr(db, "name") else None)
        if len(names) != len(set(names)):
            raise ValueError("数据库名称必须唯一")
        return v


# ── 配置加载 ─────────────────────────────────────────────

def load_config(config_path: str | Path = "config.yaml") -> AppConfig:
    """从 YAML 文件加载配置，自动解析环境变量。"""
    path = Path(config_path)
    if not path.exists():
        raise FileNotFoundError(f"配置文件不存在: {path}")

    with open(path, encoding="utf-8") as f:
        raw = yaml.safe_load(f)

    if raw is None:
        raw = {}

    # 递归解析环境变量
    resolved = _deep_resolve(raw)

    return AppConfig(**resolved)
