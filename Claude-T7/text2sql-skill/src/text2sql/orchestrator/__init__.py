"""查询编排器 — 核心入口，协调所有模块。"""

from __future__ import annotations

import logging
import time
from typing import Any

from text2sql.config import AppConfig, DatabaseConfig
from text2sql.knowledge.base import KnowledgeStore
from text2sql.llm.autofix import AutoFixEngine, SQLValidator
from text2sql.llm.models import SqlResult
from text2sql.llm.rag import RAGPipeline
from text2sql.llm.router import DBRouter
from text2sql.orchestrator.models import AskResult, AskStatus
from text2sql.runner.base import SqlRunner
from text2sql.schema.base import SchemaReader

logger = logging.getLogger(__name__)


# ── 工厂函数 ──────────────────────────────────────────────

def _create_schema_reader(db_config: DatabaseConfig) -> SchemaReader:
    """根据数据库配置创建对应的 SchemaReader。"""
    if db_config.type == "clickhouse":
        from text2sql.schema.clickhouse import CHSchemaReader
        return CHSchemaReader(
            host=db_config.host, port=db_config.port,
            username=db_config.username, password=db_config.password,
            database=db_config.database,
        )
    elif db_config.type == "postgresql":
        from text2sql.schema.postgresql import PGSchemaReader
        return PGSchemaReader(
            host=db_config.host, port=db_config.port,
            username=db_config.username, password=db_config.password,
            database=db_config.database, schema_=db_config.schema_,
        )
    elif db_config.type == "sqlite":
        from text2sql.schema.sqlite import SQLiteSchemaReader
        return SQLiteSchemaReader(db_config.database, db_name=db_config.name)
    else:
        raise ValueError(f"不支持的数据库类型: {db_config.type}")


def _create_runner(db_config: DatabaseConfig) -> SqlRunner:
    """根据数据库配置创建对应的 SqlRunner。"""
    if db_config.type == "clickhouse":
        from text2sql.runner.clickhouse import CHSqlRunner
        return CHSqlRunner(
            host=db_config.host, port=db_config.port,
            username=db_config.username, password=db_config.password,
            database=db_config.database, db_name=db_config.name,
        )
    elif db_config.type == "postgresql":
        from text2sql.runner.postgresql import PGSqlRunner
        return PGSqlRunner(
            host=db_config.host, port=db_config.port,
            username=db_config.username, password=db_config.password,
            database=db_config.database, db_name=db_config.name,
            schema_=db_config.schema_,
        )
    elif db_config.type == "sqlite":
        from text2sql.runner.sqlite import SQLiteSqlRunner
        return SQLiteSqlRunner(db_config.database, db_name=db_config.name)
    else:
        raise ValueError(f"不支持的数据库类型: {db_config.type}")


# ── 编排器 ────────────────────────────────────────────────

class QueryOrchestrator:
    """查询编排器 — Text-to-SQL Skill 的核心入口。

    协调 SchemaReader、KnowledgeStore、LLMService、SqlRunner 等模块。
    """

    def __init__(self, config: AppConfig, knowledge: KnowledgeStore,
                 llm_service=None):
        self.config = config
        self.knowledge = knowledge
        self.llm = llm_service
        self._readers: dict[str, SchemaReader] = {}
        self._runners: dict[str, SqlRunner] = {}

        # 子系统
        self.validator = SQLValidator(config.security)
        self.autofix = AutoFixEngine(llm_service=llm_service)
        self.rag = RAGPipeline(knowledge=knowledge, top_k=config.knowledge.top_k)
        self.router = DBRouter(config=config, knowledge=knowledge, llm_service=llm_service)

    def _get_reader(self, db_name: str) -> SchemaReader:
        """获取或创建指定数据库的 SchemaReader。"""
        if db_name not in self._readers:
            db_config = next(db for db in self.config.databases if db.name == db_name)
            self._readers[db_name] = _create_schema_reader(db_config)
        return self._readers[db_name]

    def _get_runner(self, db_name: str) -> SqlRunner:
        """获取或创建指定数据库的 SqlRunner。"""
        if db_name not in self._runners:
            db_config = next(db for db in self.config.databases if db.name == db_name)
            self._runners[db_name] = _create_runner(db_config)
        return self._runners[db_name]

    # ── 快速路径阈值 ───────────────────────────────────────

    _CACHE_MATCH_THRESHOLD = 0.5  # Q-SQL 距离低于此值直接复用（ChromaDB cosine distance, 精确匹配≈0.48）

    # ── 核心管线: ask ─────────────────────────────────────

    def ask(self, question: str, db_name: str | None = None,
            history: list[dict] | None = None) -> AskResult:
        """完整查询管线：路由+RAG并行 → 快速路径检测 → LLM → 验证 → 执行 → 修复 → 结果。

        Args:
            question: 用户自然语言问题
            db_name: 指定目标数据库（可选，不指定则自动路由）
            history: 对话历史

        Returns:
            AskResult 封装完整结果
        """
        start = time.monotonic()
        result = AskResult(question=question)

        # 1. 路由 + RAG 并行启动
        from concurrent.futures import ThreadPoolExecutor, as_completed

        if db_name:
            db_configs = [db for db in self.config.databases if db.name == db_name]
            if not db_configs:
                result.status = AskStatus.NO_DATABASE
                result.error = f"未找到数据库: {db_name}"
                result.elapsed_ms = (time.monotonic() - start) * 1000
                return result
            target_db = db_configs[0]
            # 已知数据库时直接做 RAG
            sql_prompt, rag_ctx = self.rag.build_prompt(
                question, db_type=target_db.type, db_name=target_db.name, history=history,
            )
        else:
            # 未知数据库：路由和 RAG（全局搜索）并行
            with ThreadPoolExecutor(max_workers=2) as pool:
                route_future = pool.submit(self.router.route, question)
                rag_future = pool.submit(
                    self.rag.build_prompt, question, "", None, history,
                )
                route_result = route_future.result()
                rag_result = rag_future.result()

            if not route_result.primary:
                result.status = AskStatus.NO_DATABASE
                result.error = "无可用数据库"
                result.elapsed_ms = (time.monotonic() - start) * 1000
                return result
            target_db = next(
                db for db in self.config.databases if db.name == route_result.primary.name
            )

            # 用目标数据库重新过滤 RAG 结果（如果全局搜索结果不匹配）
            sql_prompt, rag_ctx = rag_result
            if sql_prompt.ddl_context and target_db.name not in sql_prompt.ddl_context:
                # 全局搜索的 DDL 不属于目标库，重新搜索
                sql_prompt, rag_ctx = self.rag.build_prompt(
                    question, db_type=target_db.type, db_name=target_db.name, history=history,
                )

        result.db_name = target_db.name
        result.db_type = target_db.type

        # 2. 快速路径：检查是否有高度相似的历史问答对可复用
        cached_sql = self._try_cache_hit(question, target_db.name, rag_ctx)
        if cached_sql:
            logger.info("快速路径命中，跳过 LLM: %s → %s", question[:30], cached_sql[:60])
            sql = cached_sql
            result.sql = sql
        else:
            # 3. LLM：生成 SQL
            if not self.llm:
                result.status = AskStatus.SQL_GENERATION_FAILED
                result.error = "LLM 服务未配置"
                result.elapsed_ms = (time.monotonic() - start) * 1000
                return result

            sql_result: SqlResult = self.llm.generate_sql(sql_prompt)
            result.sql_result = sql_result

            if not sql_result.success or not sql_result.sql:
                result.status = AskStatus.SQL_GENERATION_FAILED
                result.error = sql_result.error or "LLM 未能生成 SQL"
                result.elapsed_ms = (time.monotonic() - start) * 1000
                return result

            sql = sql_result.sql
            result.sql = sql

        # 4. 安全验证
        validation = self.validator.validate(sql)
        result.validation = validation

        if validation.has_errors:
            result.status = AskStatus.VALIDATION_FAILED
            result.error = "; ".join(validation.errors)
            result.elapsed_ms = (time.monotonic() - start) * 1000
            return result

        # 5. 执行 SQL
        try:
            runner = self._get_runner(target_db.name)
        except Exception as e:
            result.status = AskStatus.EXECUTION_FAILED
            result.error = f"无法创建执行引擎: {e}"
            result.elapsed_ms = (time.monotonic() - start) * 1000
            return result

        query_result = runner.execute(
            sql, max_rows=target_db.max_rows, timeout=target_db.timeout,
        )

        # 6. 如果执行失败，尝试自动修复
        if not query_result.success and self.llm:
            autofix_result = self.autofix.try_fix(
                sql=sql,
                error=query_result.error or "",
                ddl_context=sql_prompt.ddl_context,
                db_type=target_db.type,
                runner=runner,
                max_rows=target_db.max_rows,
                timeout=target_db.timeout,
            )
            result.autofix = autofix_result

            if autofix_result.success:
                result.status = AskStatus.FIXED
                result.sql = autofix_result.sql
                # 重新执行获取结果
                query_result = runner.execute(
                    autofix_result.sql,
                    max_rows=target_db.max_rows,
                    timeout=target_db.timeout,
                )
            else:
                result.status = AskStatus.EXECUTION_FAILED
                result.error = f"SQL 执行失败且自动修复未成功: {query_result.error}"
                result.query_result = query_result
                result.elapsed_ms = (time.monotonic() - start) * 1000
                return result
        elif not query_result.success:
            result.status = AskStatus.EXECUTION_FAILED
            result.error = query_result.error or "执行失败"
            result.query_result = query_result
            result.elapsed_ms = (time.monotonic() - start) * 1000
            return result

        # 7. 成功
        result.status = AskStatus.SUCCESS if not result.autofix else AskStatus.FIXED
        result.query_result = query_result
        result.elapsed_ms = (time.monotonic() - start) * 1000

        logger.info(
            "ask 完成: %s → %s (%s) %.1fms",
            question[:30], result.sql[:60], result.status.value, result.elapsed_ms,
        )
        return result

    def _try_cache_hit(self, question: str, db_name: str, rag_ctx) -> str | None:
        """检查知识库中是否有高度相似的历史问答对可复用。

        如果 Q-SQL 距离低于阈值，直接返回历史 SQL，跳过 LLM 调用。
        """
        try:
            if rag_ctx and rag_ctx.qsql_pairs:
                best = min(rag_ctx.qsql_pairs, key=lambda x: x.distance)
                if best.distance < self._CACHE_MATCH_THRESHOLD:
                    return best.sql
        except Exception:
            pass
        return None

    # ── Schema 采集 ───────────────────────────────────────

    def auto_train_schema(self) -> dict[str, dict[str, Any]]:
        """自动采集所有数据库的 Schema 并训练。

        Returns:
            {db_name: {"tables": int, "columns": int, "trained": int}}
        """
        results: dict[str, dict[str, Any]] = {}

        for db_config in self.config.databases:
            db_name = db_config.name
            logger.info("开始采集 Schema: %s (%s)", db_name, db_config.type)

            try:
                reader = self._get_reader(db_name)
                tables = reader.read_tables()
                total_columns = 0
                trained = 0

                for table_meta in tables:
                    columns = reader.read_columns(table_meta.name)
                    table_meta.columns = columns
                    total_columns += len(columns)

                    ddl_text = table_meta.to_ddl()
                    # 构建中文别名：表注释 + 列注释 + 列名
                    alias_parts = []
                    if table_meta.comment:
                        alias_parts.append(table_meta.comment)
                    for col in columns:
                        if col.comment and col.comment not in alias_parts:
                            alias_parts.append(col.comment)
                    chinese_alias = " ".join(alias_parts[:15])  # 限制长度

                    self.knowledge.add_ddl(
                        db_name, table_meta.name, ddl_text,
                        chinese_alias=chinese_alias,
                    )
                    trained += 1

                results[db_name] = {
                    "tables": len(tables),
                    "columns": total_columns,
                    "trained": trained,
                    "status": "success",
                }
                logger.info(
                    "Schema 采集完成: %s — %d 表, %d 列, %d DDL 已训练",
                    db_name, len(tables), total_columns, trained,
                )
            except Exception as e:
                results[db_name] = {
                    "tables": 0, "columns": 0, "trained": 0,
                    "status": "error", "error": str(e),
                }
                logger.error("Schema 采集失败: %s — %s", db_name, e)

        return results

    def train(self, *, ddl: str | None = None, documentation: str | None = None,
              question: str | None = None, sql: str | None = None,
              db: str | None = None) -> str | None:
        """手动训练接口。"""
        if ddl:
            return self.knowledge.add_ddl(db or "default", "", ddl)
        if documentation:
            return self.knowledge.add_documentation(documentation)
        if question and sql:
            return self.knowledge.add_qsql_pair(question, sql, db or "default")
        return None
