"""多库智能路由 — 根据问题语义自动判断目标数据库。"""

from __future__ import annotations

import hashlib
import json
import logging
import re
import time
from dataclasses import dataclass, field

from text2sql.config import AppConfig, DatabaseConfig

logger = logging.getLogger(__name__)


@dataclass
class DBTarget:
    """路由目标数据库。"""

    name: str
    db_type: str
    relevance: float = 0.0
    reason: str = ""


@dataclass
class RouteResult:
    """路由结果。"""

    targets: list[DBTarget] = field(default_factory=list)
    cached: bool = False
    elapsed_ms: float = 0.0

    @property
    def primary(self) -> DBTarget | None:
        return self.targets[0] if self.targets else None


class DBRouter:
    """多库智能路由器。

    三级路由策略：
    1. 缓存命中：直接返回（< 50ms）
    2. RAG 路由：用知识库向量搜索确定最相关的数据库（快速、准确）
    3. LLM 路由：将表摘要发给 LLM 判断（兜底）
    """

    def __init__(self, config: AppConfig, knowledge=None, llm_service=None):
        self.config = config
        self.knowledge = knowledge
        self.llm = llm_service
        self._cache: dict[str, RouteResult] = {}
        self._table_summary: str | None = None

    def _build_table_summary(self) -> str:
        """构建所有数据库表名+注释的摘要，用于 LLM 路由。"""
        if self._table_summary is not None:
            return self._table_summary
        lines = []
        for db in self.config.databases:
            lines.append(f"### 数据库: {db.name} (类型: {db.type})")
            lines.append(f"Schema 采样表将在运行时动态加载")
        self._table_summary = "\n".join(lines)
        return self._table_summary

    def set_table_summary(self, summary: str):
        """外部设置表摘要（由 SchemaReader 采集后设置）。"""
        self._table_summary = summary

    def _cache_key(self, question: str) -> str:
        return hashlib.md5(question.strip().lower().encode()).hexdigest()[:10]

    def route(self, question: str, table_summary: str | None = None) -> RouteResult:
        """路由用户问题到目标数据库。"""
        start = time.monotonic()

        # 1. 缓存命中
        key = self._cache_key(question)
        if key in self._cache:
            result = self._cache[key]
            elapsed_ms = (time.monotonic() - start) * 1000
            result.cached = True
            result.elapsed_ms = elapsed_ms
            logger.debug("路由缓存命中: %s → %s (%.1fms)", question[:30], result.primary, elapsed_ms)
            return result

        # 2. RAG 路由：搜索每个数据库的 DDL，找到最相关的
        targets = self._rag_route(question)
        if targets:
            result = RouteResult(targets=targets, elapsed_ms=(time.monotonic() - start) * 1000)
            self._cache[key] = result
            return result

        # 3. LLM 路由
        if self.llm:
            targets = self._llm_route(question, table_summary)

        # 回退：返回所有配置的数据库
        if not targets:
            targets = [
                DBTarget(name=db.name, db_type=db.type, relevance=0.5, reason="默认路由：返回所有数据库")
                for db in self.config.databases
            ]

        result = RouteResult(targets=targets, elapsed_ms=(time.monotonic() - start) * 1000)
        self._cache[key] = result
        logger.info("路由结果: %s → %s (%.1fms)", question[:30],
                     [t.name for t in targets], result.elapsed_ms)
        return result

    def _rag_route(self, question: str) -> list[DBTarget] | None:
        """基于知识库 RAG 搜索的路由 — 并行搜索所有数据库。"""
        if not self.knowledge:
            return None

        from concurrent.futures import ThreadPoolExecutor, as_completed

        def _score_db(db: DatabaseConfig) -> tuple[float, DatabaseConfig, str] | None:
            try:
                ddls = self.knowledge.search_ddl(question, top_k=3, db_name=db.name)
                if not ddls:
                    return None
                best_dist = min(d.distance for d in ddls)
                tables = [d.table for d in ddls]
                return (best_dist, db, f"匹配表: {', '.join(tables[:3])}")
            except Exception:
                return None

        # 并行搜索所有数据库
        scored: list[tuple[float, DatabaseConfig, str]] = []
        with ThreadPoolExecutor(max_workers=min(len(self.config.databases), 4)) as pool:
            futures = {pool.submit(_score_db, db): db for db in self.config.databases}
            for future in as_completed(futures):
                result = future.result()
                if result:
                    scored.append(result)

        if not scored:
            return None

        # 按距离排序（越小越相关）
        scored.sort(key=lambda x: x[0])
        result = []
        for dist, cfg, reason in scored:
            relevance = max(0.1, 1.0 - dist)
            result.append(DBTarget(name=cfg.name, db_type=cfg.type, relevance=relevance, reason=reason))
        return result

    def _llm_route(self, question: str, table_summary: str | None = None) -> list[DBTarget] | None:
        """使用 LLM 进行语义路由。"""
        summary = table_summary or self._build_table_summary()
        db_list = ", ".join(f"{db.name}({db.type})" for db in self.config.databases)

        prompt = f"""根据以下数据库和表信息，判断用户问题应该查询哪个数据库。

数据库列表: {db_list}

{summary}

用户问题: {question}

返回 JSON 数组格式（不要其他文字）:
[{{"db": "数据库名", "reason": "判断原因", "confidence": 0.9}}]"""

        try:
            raw = self.llm._chat([
                {"role": "system", "content": "你是一个数据库路由专家，只输出 JSON，不要其他文字。"},
                {"role": "user", "content": prompt},
            ])

            # 提取 JSON
            json_match = re.search(r'\[.*\]', raw, re.DOTALL)
            if not json_match:
                return None

            items = json.loads(json_match.group())
            db_map = {db.name: db for db in self.config.databases}
            targets = []
            for item in items:
                name = item.get("db", "")
                if name in db_map:
                    targets.append(DBTarget(
                        name=name,
                        db_type=db_map[name].type,
                        relevance=float(item.get("confidence", 0.5)),
                        reason=item.get("reason", ""),
                    ))
            return targets if targets else None

        except Exception as e:
            logger.warning("LLM 路由失败: %s", e)
            return None

    def explain_routing(self, question: str) -> str:
        """查看路由判断原因。"""
        result = self.route(question)
        lines = [f"问题: {question}", f"缓存命中: {'是' if result.cached else '否'}"]
        for i, t in enumerate(result.targets, 1):
            lines.append(f"  目标{i}: {t.name} ({t.db_type}) 相关度={t.relevance:.2f} 原因={t.reason}")
        return "\n".join(lines)

    def clear_cache(self):
        self._cache.clear()
