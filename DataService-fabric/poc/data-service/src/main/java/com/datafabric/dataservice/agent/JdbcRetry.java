package com.datafabric.dataservice.agent;

import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;

/**
 * F8 瞬时故障重试（B 路工具共用）
 *
 * 只重试"值得重试"的故障：SQLTransientException / SQLRecoverableException /
 * SQLState 08*（连接类异常，驱动各自子类最终都落到这几类）。语法错、表不存在
 * 等确定性失败立即上抛——重试只是浪费延迟。
 *
 * attempts 计入 Outcome，供 F2 工具日志 summary（rows=N retries=M）。
 */
final class JdbcRetry {

    private static final int MAX_ATTEMPTS = 2;
    private static final long BACKOFF_MS = 200;

    record Outcome<T>(T value, int attempts) {}

    @FunctionalInterface
    interface SqlAction<T> {
        T run() throws Exception;
    }

    private JdbcRetry() {
    }

    static <T> Outcome<T> withRetry(SqlAction<T> action) throws Exception {
        int attempt = 1;
        while (true) {
            try {
                return new Outcome<>(action.run(), attempt);
            } catch (Exception e) {
                if (attempt >= MAX_ATTEMPTS || !isTransient(e)) {
                    throw e;
                }
                attempt++;
                Thread.sleep(BACKOFF_MS);
            }
        }
    }

    /** 沿 cause 链找瞬时 SQLException（驱动常把真因包在 RuntimeException 里） */
    static boolean isTransient(Throwable e) {
        for (Throwable cur = e; cur != null; cur = cur.getCause()) {
            if (cur instanceof SQLTransientException || cur instanceof SQLRecoverableException) {
                return true;
            }
            if (cur instanceof SQLException se
                    && se.getSQLState() != null
                    && se.getSQLState().startsWith("08")) {
                return true;
            }
        }
        return false;
    }
}
