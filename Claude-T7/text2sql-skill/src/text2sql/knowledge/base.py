"""KnowledgeStore 抽象基类。"""

from __future__ import annotations

from abc import ABC, abstractmethod

from text2sql.knowledge.models import (
    RAGContext,
    TrainingDataItem,
)


class KnowledgeStore(ABC):
    """知识库存储抽象基类。

    管理 DDL、文档、历史 Q-SQL 对的向量索引与检索。
    默认实现为 ChromaDB，可替换为 pgvector 等后端。
    """

    @abstractmethod
    def add_ddl(self, db: str, table: str, ddl: str) -> str:
        """存储 DDL，返回条目 ID。"""

    @abstractmethod
    def add_documentation(self, content: str, metadata: dict | None = None) -> str:
        """存储业务文档，返回条目 ID。"""

    @abstractmethod
    def add_qsql_pair(self, question: str, sql: str, db: str) -> str:
        """存储问题-SQL 对，返回条目 ID。"""

    @abstractmethod
    def search_ddl(self, query: str, top_k: int = 5, db_name: str | None = None) -> list:
        """检索相关 DDL。"""

    @abstractmethod
    def search_docs(self, query: str, top_k: int = 5) -> list:
        """检索相关文档。"""

    @abstractmethod
    def search_qsql(self, query: str, top_k: int = 5, db_name: str | None = None) -> list:
        """检索相似 Q-SQL 对。"""

    @abstractmethod
    def search_all(self, query: str, top_k: int = 5, db_name: str | None = None) -> RAGContext:
        """从三个 collection 检索并聚合结果。"""

    @abstractmethod
    def list_training_data(self) -> list[TrainingDataItem]:
        """列出所有训练数据。"""

    @abstractmethod
    def remove_training_data(self, item_id: str) -> bool:
        """删除指定训练数据。"""

    @abstractmethod
    def clear_collection(self, collection_type: str) -> int:
        """清空指定类型的知识库（ddl / documentation / question_sql），返回删除数量。"""

    @abstractmethod
    def get_stats(self) -> dict:
        """返回知识库统计信息。"""
