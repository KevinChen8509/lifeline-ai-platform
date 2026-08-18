package com.datafabric.dataservice.agent;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F8 JdbcRetry：瞬时故障重试 / 确定性失败立即上抛
 */
class JdbcRetryTest {

    @Test
    void withRetry_successFirstAttempt() throws Exception {
        JdbcRetry.Outcome<String> out = JdbcRetry.withRetry(() -> "ok");

        assertThat(out.value()).isEqualTo("ok");
        assertThat(out.attempts()).isEqualTo(1);
    }

    @Test
    void withRetry_transientFailureRetriesAndSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        JdbcRetry.Outcome<String> out = JdbcRetry.withRetry(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new SQLTransientConnectionException("connection reset");
            }
            return "recovered";
        });

        assertThat(out.value()).isEqualTo("recovered");
        assertThat(out.attempts()).isEqualTo(2);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void withRetry_exhaustsAfterMaxAttempts() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> JdbcRetry.withRetry(() -> {
            calls.incrementAndGet();
            throw new SQLTransientConnectionException("still down");
        })).isInstanceOf(SQLTransientConnectionException.class);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void withRetry_nonTransientFailsImmediately() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> JdbcRetry.withRetry(() -> {
            calls.incrementAndGet();
            // 42000 = 语法错误，重试无意义
            throw new SQLException("syntax error", "42000");
        })).isInstanceOf(SQLException.class);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void isTransient_walksCauseChain() {
        SQLException connectionException = new SQLException("connect failed", "08001");
        assertThat(JdbcRetry.isTransient(connectionException)).isTrue();
        assertThat(JdbcRetry.isTransient(new SQLTransientConnectionException("reset"))).isTrue();

        // 驱动常把 SQLException 包在 RuntimeException 里
        assertThat(JdbcRetry.isTransient(new RuntimeException("wrapped", connectionException))).isTrue();

        assertThat(JdbcRetry.isTransient(new SQLException("syntax", "42000"))).isFalse();
        assertThat(JdbcRetry.isTransient(new IllegalArgumentException("bad args"))).isFalse();
    }
}
