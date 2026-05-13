"""Prompt 模板 — SQL 生成、修复、摘要。"""

from text2sql.llm.models import SqlPrompt

SQL_GENERATION_PROMPT = """根据以下信息生成 SQL 查询。

数据库类型: {db_type}

## 表结构:
{ddl_context}

## 业务文档:
{doc_context}

## 相似查询参考:
{qsql_context}

## 问题:
{question}

要求:
1. 只生成 {db_type} 兼容的 SQL
2. 使用上述表结构中的表和字段
3. SQL 用 ```sql ``` 包裹
4. 无法确定时返回 "无法生成SQL: 原因"

SQL:"""

SQL_FIX_PROMPT = """修复以下出错的 SQL。

数据库类型: {db_type}

原始 SQL:
```sql
{sql}
```

错误: {error}

表结构:
{ddl}

修复后 SQL (```sql ``` 包裹):"""

SUMMARIZE_PROMPT = """简要总结查询结果。

问题: {question}
结果: {data}

中文总结（不超过3句）:"""


def build_sql_prompt(prompt: SqlPrompt) -> str:
    """组装完整的 SQL 生成 Prompt。"""
    return SQL_GENERATION_PROMPT.format(
        db_type=prompt.db_type or "SQL",
        ddl_context=prompt.ddl_context or "（无）",
        doc_context=prompt.doc_context or "（无）",
        qsql_context=prompt.qsql_context or "（无）",
        question=prompt.question,
    )


def build_fix_prompt(sql: str, error: str, ddl: str, db_type: str = "SQL") -> str:
    """组装 SQL 修复 Prompt。"""
    return SQL_FIX_PROMPT.format(
        db_type=db_type, sql=sql, error=error, ddl=ddl,
    )


def build_summarize_prompt(question: str, data: str) -> str:
    """组装摘要生成 Prompt。"""
    return SUMMARIZE_PROMPT.format(question=question, data=data)
