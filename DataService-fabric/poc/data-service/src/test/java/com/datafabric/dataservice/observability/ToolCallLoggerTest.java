package com.datafabric.dataservice.observability;

import com.datafabric.dataservice.governance.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2 ToolCallLogger：记录 / 聚合 / 过滤 / 容量淘汰 / requestId 解析
 */
class ToolCallLoggerTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
        UsageContext.clear();
    }

    @Test
    void record_snapshotReturnsLatestFirst() {
        ToolCallLogger logger = new ToolCallLogger();
        logger.record("fabric", "getCustomerProfile", Map.of("custId", "C0001"), 12, true, "rows=1");
        logger.record("raw", "getCustomerRaw", Map.of("custId", "C0002"), 34, true, "rows=0");

        Map<String, Object> snapshot = logger.snapshot(null, 50);

        @SuppressWarnings("unchecked")
        List<ToolCallLogger.ToolCall> recent =
                (List<ToolCallLogger.ToolCall>) snapshot.get("recent");
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).tool()).isEqualTo("getCustomerRaw");   // 最新在前
        assertThat(recent.get(0).elapsedMs()).isEqualTo(34);
        assertThat(recent.get(1).path()).isEqualTo("fabric");
        assertThat(snapshot.get("recentCount")).isEqualTo(2);
    }

    @Test
    void record_aggregatesByToolWithFailuresAndLatency() {
        ToolCallLogger logger = new ToolCallLogger();
        logger.record("fabric", "getCustomerProfile", Map.of(), 10, true, "rows=1");
        logger.record("fabric", "getCustomerProfile", Map.of(), 30, false, "ERROR:Exception");

        @SuppressWarnings("unchecked")
        Map<String, Object> byTool =
                (Map<String, Object>) logger.snapshot(null, 50).get("byTool");
        @SuppressWarnings("unchecked")
        Map<String, Object> stat = (Map<String, Object>) byTool.get("getCustomerProfile");
        assertThat(stat)
                .containsEntry("totalCalls", 2L)
                .containsEntry("failures", 1L)
                .containsEntry("avgElapsedMs", 20L)
                .containsEntry("maxElapsedMs", 30L);
    }

    @Test
    void snapshot_filtersByPathAndLimit() {
        ToolCallLogger logger = new ToolCallLogger();
        for (int i = 0; i < 5; i++) {
            logger.record("fabric", "t-fabric-" + i, Map.of(), i, true, "ok");
        }
        for (int i = 0; i < 5; i++) {
            logger.record("raw", "t-raw-" + i, Map.of(), i, true, "ok");
        }

        Map<String, Object> rawOnly = logger.snapshot("raw", 3);
        @SuppressWarnings("unchecked")
        List<ToolCallLogger.ToolCall> recent =
                (List<ToolCallLogger.ToolCall>) rawOnly.get("recent");
        assertThat(recent).hasSize(3);
        assertThat(recent).allMatch(c -> "raw".equals(c.path()));
        // limit 3 取最新的 3 条：t-raw-4, t-raw-3, t-raw-2
        assertThat(recent.get(0).tool()).isEqualTo("t-raw-4");
        assertThat(recent.get(2).tool()).isEqualTo("t-raw-2");
    }

    @Test
    void requestId_resolvedFromTraceContextThenUsageContext() {
        ToolCallLogger logger = new ToolCallLogger();

        TraceContext.set("trace-1");
        logger.record("fabric", "toolA", Map.of(), 1, true, "ok");
        TraceContext.clear();

        UsageContext.set("raw", "usage-1");
        logger.record("raw", "toolB", Map.of(), 1, true, "ok");
        UsageContext.clear();

        logger.record("fabric", "toolC", Map.of(), 1, true, "ok");   // 流式回调线程：无上下文

        @SuppressWarnings("unchecked")
        List<ToolCallLogger.ToolCall> recent =
                (List<ToolCallLogger.ToolCall>) logger.snapshot(null, 10).get("recent");
        assertThat(recent.get(2).requestId()).isEqualTo("trace-1");
        assertThat(recent.get(1).requestId()).isEqualTo("usage-1");
        assertThat(recent.get(0).requestId()).isNull();
    }

    @Test
    void recent_boundedAt200() {
        ToolCallLogger logger = new ToolCallLogger();
        for (int i = 0; i < 230; i++) {
            logger.record("fabric", "tool-" + i, Map.of(), i, true, "ok");
        }
        @SuppressWarnings("unchecked")
        List<ToolCallLogger.ToolCall> recent =
                (List<ToolCallLogger.ToolCall>) logger.snapshot(null, 200).get("recent");
        assertThat(recent).hasSize(200);
        assertThat(recent.get(0).tool()).isEqualTo("tool-229");
        assertThat(recent.get(199).tool()).isEqualTo("tool-30");
    }
}
