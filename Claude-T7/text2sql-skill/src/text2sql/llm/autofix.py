"""SQL 安全检查与自动修复引擎。"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass, field

from text2sql.config import SecurityConfig

logger = logging.getLogger(__name__)


@dataclass
class ValidationResult:
    """SQL 验证结果。"""

    ok: bool = True
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    @property
    def has_errors(self) -> bool:
        return not self.ok


class SQLValidator:
    """SQL 安全检查器 — 在执行前拦截危险语句。"""

    # SQL 注入危险模式
    _INJECTION_PATTERNS = [
        (re.compile(r";\s*(DROP|DELETE|TRUNCATE|ALTER|INSERT|UPDATE|GRANT|REVOKE)\b", re.IGNORECASE),
         "检测到潜在 SQL 注入: 分号后跟危险操作"),
        (re.compile(r"UNION\s+SELECT", re.IGNORECASE),
         "UNION SELECT 可能用于数据窃取"),
        (re.compile(r"INTO\s+OUTFILE", re.IGNORECASE),
         "INTO OUTFILE 可能用于文件系统访问"),
        (re.compile(r"LOAD_FILE\s*\(", re.IGNORECASE),
         "LOAD_FILE 可能用于读取服务器文件"),
        (re.compile(r"0x[0-9a-fA-F]{6,}", re.IGNORECASE),
         "长十六进制字符串可能用于混淆攻击"),
        (re.compile(r"CHAR\s*\(\s*\d+[\s,]*\d*", re.IGNORECASE),
         "CHAR() 编码可能用于绕过过滤"),
    ]

    def __init__(self, config: SecurityConfig):
        self.config = config

    def validate(self, sql: str) -> ValidationResult:
        """验证 SQL 是否安全可执行。"""
        result = ValidationResult()
        stripped = sql.strip().upper()

        # 1. 空语句检查
        if not stripped:
            result.ok = False
            result.errors.append("SQL 语句为空")
            return result

        # 2. 只读模式：只允许 SELECT / WITH / EXPLAIN
        if self.config.mode == "readonly":
            if not (stripped.startswith("SELECT")
                    or stripped.startswith("WITH")
                    or stripped.startswith("EXPLAIN")):
                result.ok = False
                result.errors.append(
                    f"只读模式不允许执行: {stripped.split()[0]}"
                )

        # 3. 关键词黑名单
        keywords = re.findall(r'\b[A-Z]+\b', stripped)
        blocked = [kw for kw in keywords if kw in self.config.blocked_keywords]
        if blocked:
            result.ok = False
            result.errors.append(f"包含被禁止的关键词: {', '.join(blocked)}")

        # 4. 多语句检测（分号分隔）
        # 去掉字符串内的分号
        clean = re.sub(r"'[^']*'", "", sql)
        statements = [s.strip() for s in clean.split(";") if s.strip()]
        if len(statements) > 1:
            result.warnings.append(f"检测到 {len(statements)} 条语句，仅执行第一条")

        # 5. 注释注入检测
        if "--" in sql and not sql.strip().startswith("--"):
            result.warnings.append("SQL 包含行内注释")

        # 6. SQL 注入模式检测
        for pattern, message in self._INJECTION_PATTERNS:
            if pattern.search(sql):
                result.ok = False
                result.errors.append(message)
                break  # 报一个即可

        # 7. 子查询深度限制（防止 DoS）
        paren_count = sql.count("(")
        if paren_count > 10:
            result.warnings.append(f"括号嵌套数 {paren_count} 可能影响性能")

        # 8. LIKE 通配符检查
        if re.search(r"LIKE\s+'%.*%.*%'", sql, re.IGNORECASE):
            result.warnings.append("前后双 % 的 LIKE 可能导致全表扫描")

        return result


@dataclass
class FixAttempt:
    """单次修复尝试记录。"""

    attempt: int
    original_sql: str
    original_error: str
    fixed_sql: str | None = None
    fixed_error: str | None = None
    success: bool = False


@dataclass
class AutoFixResult:
    """自动修复结果。"""

    sql: str
    success: bool
    attempts: list[FixAttempt] = field(default_factory=list)
    total_fixes: int = 0

    @property
    def was_fixed(self) -> bool:
        """是否经过修复后成功。"""
        return self.success and self.total_fixes > 0


class AutoFixEngine:
    """SQL 自动修复引擎 — 捕获执行错误，调用 LLM 修复并重试。"""

    def __init__(self, llm_service=None, max_retries: int = 1):
        self.llm = llm_service
        self.max_retries = max_retries

    def try_fix(self, sql: str, error: str, ddl_context: str = "",
                db_type: str = "SQL", runner=None,
                max_rows: int = 10000, timeout: int = 30) -> AutoFixResult:
        """尝试自动修复 SQL。

        流程: 原始 SQL 失败 → LLM 修复 → 重试执行 → 最多 max_retries 轮
        """
        result = AutoFixResult(sql=sql, success=False)

        if not self.llm:
            logger.warning("无 LLM 服务，无法自动修复")
            return result

        current_sql = sql
        current_error = error

        for attempt_num in range(1, self.max_retries + 1):
            attempt = FixAttempt(
                attempt=attempt_num,
                original_sql=current_sql,
                original_error=current_error,
            )

            # 调用 LLM 修复
            logger.info("SQL 自动修复第 %d 次尝试: %s", attempt_num, current_sql[:60])
            fix_result = self.llm.fix_sql(
                sql=current_sql,
                error=current_error,
                ddl=ddl_context,
                db_type=db_type,
            )

            if not fix_result.success or not fix_result.sql:
                attempt.fixed_error = fix_result.error or "LLM 未能生成修复 SQL"
                result.attempts.append(attempt)
                logger.warning("LLM 修复失败: %s", attempt.fixed_error)
                break

            attempt.fixed_sql = fix_result.sql

            # 如果有 runner，重新执行验证
            if runner:
                exec_result = runner.execute(attempt.fixed_sql, max_rows=max_rows, timeout=timeout)
                if exec_result.success:
                    attempt.success = True
                    result.sql = attempt.fixed_sql
                    result.success = True
                    result.total_fixes = attempt_num
                    result.attempts.append(attempt)
                    logger.info("SQL 自动修复成功（第 %d 次）", attempt_num)
                    return result
                else:
                    current_error = exec_result.error or "未知执行错误"
                    attempt.fixed_error = current_error
                    current_sql = attempt.fixed_sql
            else:
                # 无 runner，信任 LLM 输出
                attempt.success = True
                result.sql = attempt.fixed_sql
                result.success = True
                result.total_fixes = attempt_num
                result.attempts.append(attempt)
                return result

            result.attempts.append(attempt)

        logger.warning("SQL 自动修复失败，已达最大重试次数: %d", self.max_retries)
        return result
