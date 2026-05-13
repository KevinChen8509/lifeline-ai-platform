"""RAG 上下文组装管线。"""

from __future__ import annotations

import logging
import time

from text2sql.knowledge.base import KnowledgeStore
from text2sql.knowledge.models import RAGContext
from text2sql.llm.models import SqlPrompt

logger = logging.getLogger(__name__)


class RAGPipeline:
    """RAG 检索增强管线：将用户问题转化为带上下文的 SqlPrompt。

    流程：question → KnowledgeStore.search_all() → RAGContext → SqlPrompt
    """

    def __init__(self, knowledge: KnowledgeStore, top_k: int = 5):
        self.knowledge = knowledge
        self.top_k = top_k

    def build_prompt(self, question: str, db_type: str = "",
                     db_name: str | None = None,
                     history: list[dict] | None = None) -> tuple[SqlPrompt, RAGContext]:
        """根据用户问题检索上下文并组装 SqlPrompt。

        Returns:
            (SqlPrompt, RAGContext) — 带 RAG 上下文的 Prompt 和原始检索结果
        """
        start = time.monotonic()

        # 1. 检索三类知识（按目标数据库过滤）
        context = self.knowledge.search_all(question, top_k=self.top_k, db_name=db_name)

        elapsed_ms = (time.monotonic() - start) * 1000
        logger.info(
            "RAG 检索完成: DDL=%d, Doc=%d, QSQL=%d, 耗时=%.1fms",
            len(context.ddls), len(context.docs), len(context.qsql_pairs), elapsed_ms,
        )

        # 2. 分别格式化三类上下文
        ddl_context = self._format_ddls(context)
        doc_context = self._format_docs(context)
        qsql_context = self._format_qsql(context)

        # 3. 组装 SqlPrompt
        prompt = SqlPrompt(
            question=question,
            db_type=db_type,
            ddl_context=ddl_context,
            doc_context=doc_context,
            qsql_context=qsql_context,
            history=history or [],
        )

        return prompt, context

    def _format_ddls(self, context: RAGContext) -> str:
        """格式化 DDL 上下文 — 精简版，去掉冗余注释。"""
        if not context.ddls:
            return ""
        lines = []
        for ddl in context.ddls:
            # 去掉 "-- 中文别名:" 尾部注释（搜索用，LLM 不需要）
            ddl_text = ddl.ddl.split("\n-- 中文别名:")[0]
            lines.append(f"-- 数据库: {ddl.database} 表: {ddl.table}")
            lines.append(ddl_text.strip())
        return "\n\n".join(lines)

    def _format_docs(self, context: RAGContext) -> str:
        """格式化文档上下文。"""
        if not context.docs:
            return ""
        return "\n".join(f"- {doc.content}" for doc in context.docs)

    def _format_qsql(self, context: RAGContext) -> str:
        """格式化历史 Q-SQL 对（最高优先级，放在最前面）。"""
        if not context.qsql_pairs:
            return ""
        lines = []
        for qsql in context.qsql_pairs:
            lines.append(f"-- 相似问题: {qsql.question} (数据库: {qsql.database})")
            lines.append(qsql.sql)
        return "\n\n".join(lines)
