"""ChromaDB 知识库实现。"""

from __future__ import annotations

import logging
import uuid
from typing import Any

from text2sql.knowledge.base import KnowledgeStore
from text2sql.knowledge.models import (
    DDLResult,
    DocResult,
    QSQLResult,
    RAGContext,
    TrainingDataItem,
    TrainingDataType,
)

logger = logging.getLogger(__name__)


class ChromaDBStore(KnowledgeStore):
    """基于 ChromaDB 的知识库存储。

    使用三个独立的 Collection：
    - ddl_store: 表结构 DDL
    - doc_store: 业务文档
    - qsql_store: 问题-SQL 对
    """

    def __init__(self, persist_dir: str = "./data/knowledge", **kwargs: Any):
        try:
            import chromadb
        except ImportError:
            raise ImportError(
                "请安装 chromadb: uv pip install text2sql-skill[chromadb]"
            )
        self._chroma = chromadb
        self._client = chromadb.PersistentClient(path=persist_dir)
        self._ddl_store = self._client.get_or_create_collection(
            "ddl_store", metadata={"hnsw:space": "cosine"},
        )
        self._doc_store = self._client.get_or_create_collection(
            "doc_store", metadata={"hnsw:space": "cosine"},
        )
        self._qsql_store = self._client.get_or_create_collection(
            "qsql_store", metadata={"hnsw:space": "cosine"},
        )
        logger.info("ChromaDB 知识库已初始化 (path=%s)", persist_dir)

    @staticmethod
    def _new_id() -> str:
        return uuid.uuid4().hex[:12]

    # ── 添加 ────────────────────────────────────────────────

    def add_ddl(self, db: str, table: str, ddl: str, chinese_alias: str = "") -> str:
        # 先删除同一 db+table 的旧条目（避免重复）
        try:
            existing = self._ddl_store.get(
                where={"db": db, "table": table},
            )
            if existing["ids"]:
                self._ddl_store.delete(ids=existing["ids"])
        except Exception:
            pass

        # 如果有中文别名，追加到 DDL 文本末尾以增强中文搜索
        doc_text = ddl
        if chinese_alias:
            doc_text = f"{ddl}\n-- 中文别名: {chinese_alias}"

        item_id = self._new_id()
        self._ddl_store.add(
            ids=[item_id],
            documents=[doc_text],
            metadatas=[{"db": db, "table": table, "type": "ddl"}],
        )
        logger.debug("添加 DDL: db=%s table=%s id=%s", db, table, item_id)
        return item_id

    def add_documentation(self, content: str, metadata: dict | None = None) -> str:
        item_id = self._new_id()
        meta = {"type": "documentation"}
        if metadata:
            meta.update({k: str(v) for k, v in metadata.items()})
        self._doc_store.add(
            ids=[item_id],
            documents=[content],
            metadatas=[meta],
        )
        logger.debug("添加文档: id=%s", item_id)
        return item_id

    def add_qsql_pair(self, question: str, sql: str, db: str) -> str:
        item_id = self._new_id()
        doc = f"问题: {question}\nSQL: {sql}"
        self._qsql_store.add(
            ids=[item_id],
            documents=[doc],
            metadatas=[{"db": db, "question": question, "sql": sql, "type": "qsql"}],
        )
        logger.debug("添加 Q-SQL: db=%s id=%s", db, item_id)
        return item_id

    # ── 检索 ────────────────────────────────────────────────

    def search_ddl(self, query: str, top_k: int = 5, db_name: str | None = None) -> list[DDLResult]:
        if self._ddl_store.count() == 0:
            return []
        kwargs: dict[str, Any] = {
            "query_texts": [query],
            "n_results": min(top_k * 2, self._ddl_store.count()),
        }
        if db_name:
            kwargs["where"] = {"db": db_name}
        results = self._ddl_store.query(**kwargs)
        items = []
        seen_tables = set()
        for i, doc in enumerate(results["documents"][0]):
            meta = results["metadatas"][0][i]
            dist = results["distances"][0][i] if results.get("distances") else 0.0
            table = meta.get("table", "")
            seen_tables.add(table)
            items.append(DDLResult(
                ddl=doc, table=table,
                database=meta.get("db", ""), distance=dist,
            ))

        # 中文混合搜索：关键词全表扫描 + 距离加分
        import re
        has_chinese = bool(re.search(r'[\u4e00-\u9fff]', query))
        if has_chinese:
            cn_chars = re.findall(r'[\u4e00-\u9fff]', query)
            cn_words = set()
            for i in range(len(cn_chars) - 1):
                cn_words.add(cn_chars[i] + cn_chars[i + 1])

            # 对已检索结果做关键词加分
            for item in items:
                matched = sum(1 for w in cn_words if w in item.ddl)
                if matched > 0:
                    item.distance = max(0.01, item.distance - matched * 0.3)

            # 全表扫描：找出向量搜索未命中但关键词匹配的表
            where_filter = {"db": db_name} if db_name else None
            try:
                all_data = self._ddl_store.get(
                    where=where_filter,
                    include=["documents", "metadatas"],
                )
                for i, doc in enumerate(all_data["documents"]):
                    meta = all_data["metadatas"][i]
                    table = meta.get("table", "")
                    if table in seen_tables:
                        continue
                    matched = sum(1 for w in cn_words if w in doc)
                    if matched >= 2:  # 至少匹配2个关键词
                        items.append(DDLResult(
                            ddl=doc, table=table,
                            database=meta.get("db", ""),
                            distance=max(0.01, 0.8 - matched * 0.2),
                        ))
                        seen_tables.add(table)
            except Exception:
                pass

            items.sort(key=lambda x: x.distance)

        return items[:top_k]

    def search_docs(self, query: str, top_k: int = 5) -> list[DocResult]:
        if self._doc_store.count() == 0:
            return []
        results = self._doc_store.query(query_texts=[query], n_results=min(top_k, self._doc_store.count()))
        items = []
        for i, doc in enumerate(results["documents"][0]):
            dist = results["distances"][0][i] if results.get("distances") else 0.0
            items.append(DocResult(content=doc, distance=dist))
        return items

    def search_qsql(self, query: str, top_k: int = 5, db_name: str | None = None) -> list[QSQLResult]:
        if self._qsql_store.count() == 0:
            return []
        kwargs: dict[str, Any] = {
            "query_texts": [query],
            "n_results": min(top_k, self._qsql_store.count()),
        }
        if db_name:
            kwargs["where"] = {"db": db_name}
        results = self._qsql_store.query(**kwargs)
        items = []
        ids = results.get("ids", [[]])[0]
        for i, doc in enumerate(results["documents"][0]):
            meta = results["metadatas"][0][i]
            dist = results["distances"][0][i] if results.get("distances") else 0.0
            items.append(QSQLResult(
                question=meta.get("question", ""),
                sql=meta.get("sql", ""),
                database=meta.get("db", ""),
                distance=dist,
                item_id=ids[i] if i < len(ids) else "",
            ))
        return items

    def search_all(self, query: str, top_k: int = 5, db_name: str | None = None) -> RAGContext:
        """并行检索三类知识，减少串行等待时间。"""
        from concurrent.futures import ThreadPoolExecutor, as_completed

        results = {"ddls": [], "docs": [], "qsql_pairs": []}

        with ThreadPoolExecutor(max_workers=3) as pool:
            futures = {
                pool.submit(self.search_ddl, query, top_k, db_name): "ddls",
                pool.submit(self.search_docs, query, top_k): "docs",
                pool.submit(self.search_qsql, query, top_k, db_name): "qsql_pairs",
            }
            for future in as_completed(futures):
                key = futures[future]
                results[key] = future.result()

        return RAGContext(
            ddls=results["ddls"],
            docs=results["docs"],
            qsql_pairs=results["qsql_pairs"],
        )

    # ── 管理 ────────────────────────────────────────────────

    def list_training_data(self) -> list[TrainingDataItem]:
        items = []
        for coll, dtype in [
            (self._ddl_store, TrainingDataType.DDL),
            (self._doc_store, TrainingDataType.DOCUMENTATION),
            (self._qsql_store, TrainingDataType.QUESTION_SQL),
        ]:
            if coll.count() == 0:
                continue
            batch = coll.get(include=["documents", "metadatas"])
            for i, item_id in enumerate(batch["ids"]):
                items.append(TrainingDataItem(
                    id=item_id,
                    type=dtype,
                    content=batch["documents"][i],
                    metadata=batch["metadatas"][i],
                ))
        return items

    def remove_training_data(self, item_id: str) -> bool:
        for coll in [self._ddl_store, self._doc_store, self._qsql_store]:
            if coll.count() == 0:
                continue
            existing = coll.get(ids=[item_id])
            if existing["ids"]:
                coll.delete(ids=[item_id])
                logger.debug("删除训练数据: id=%s", item_id)
                return True
        return False

    def get_stats(self) -> dict:
        return {
            "ddl_count": self._ddl_store.count(),
            "doc_count": self._doc_store.count(),
            "qsql_count": self._qsql_store.count(),
            "total": self._ddl_store.count() + self._doc_store.count() + self._qsql_store.count(),
        }

    def clear_collection(self, collection_type: str) -> int:
        """清空指定类型的知识库，返回删除数量。"""
        mapping = {
            "ddl": self._ddl_store,
            "documentation": self._doc_store,
            "question_sql": self._qsql_store,
        }
        if collection_type == "all":
            total = 0
            for coll in mapping.values():
                count = coll.count()
                if count > 0:
                    ids = coll.get()["ids"]
                    coll.delete(ids=ids)
                    total += count
            logger.info("已清空全部知识库，共删除 %d 条", total)
            return total

        coll = mapping.get(collection_type)
        if not coll:
            raise ValueError(f"未知的知识库类型: {collection_type}")
        count = coll.count()
        if count > 0:
            ids = coll.get()["ids"]
            coll.delete(ids=ids)
        logger.info("已清空知识库 %s，删除 %d 条", collection_type, count)
        return count
