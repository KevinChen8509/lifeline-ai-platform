package com.datafabric.dataservice.observability;

import com.datafabric.dataservice.config.LlmProperties;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F6 TokenUsageStore 聚合 / 成本估算 / 容量淘汰测试
 */
class TokenUsageStoreTest {

    private static final LlmProperties PRICING = new LlmProperties(
            "http://localhost", "test-key", "test-model", null, null, null, null, null);

    @Test
    @SuppressWarnings("unchecked")
    void record_aggregatesGlobalAndByPath() {
        TokenUsageStore store = new TokenUsageStore();
        store.record("fabric", "req-1", new TokenUsage(100, 50, 150));
        store.record("fabric", "req-1", new TokenUsage(30, 20, 50));   // 工具循环第二次 LLM 调用
        store.record("raw", "req-2", new TokenUsage(10, 5, 15));

        Map<String, Object> snapshot = store.snapshot(PRICING);

        Map<String, Object> totals = (Map<String, Object>) snapshot.get("totals");
        assertThat(totals)
                .containsEntry("llmCalls", 3L)
                .containsEntry("inputTokens", 140L)
                .containsEntry("outputTokens", 75L)
                .containsEntry("totalTokens", 215L);

        Map<String, Object> byPath = (Map<String, Object>) snapshot.get("byPath");
        assertThat(byPath).containsKeys("fabric", "raw");
        assertThat((Map<String, Object>) byPath.get("fabric")).containsEntry("llmCalls", 2L);
        assertThat((Map<String, Object>) byPath.get("raw")).containsEntry("inputTokens", 10L);
    }

    @Test
    void record_nullUsage_countsCallWithZeroTokens() {
        TokenUsageStore store = new TokenUsageStore();
        store.record("fabric", "req-1", null);

        Map<String, Object> snapshot = store.snapshot(PRICING);
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) snapshot.get("totals");
        assertThat(totals).containsEntry("llmCalls", 1L).containsEntry("totalTokens", 0L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void snapshot_costEstimateUsesConfiguredPricing() {
        TokenUsageStore store = new TokenUsageStore();
        store.record("fabric", "req-1", new TokenUsage(500_000, 250_000, 750_000));

        Map<String, Object> cost = (Map<String, Object>) store.snapshot(PRICING).get("costEstimateCny");
        // 500k * 2.0/1M = 1.0；250k * 8.0/1M = 2.0
        assertThat(cost).containsEntry("input", 1.0).containsEntry("output", 2.0).containsEntry("total", 3.0);
    }

    @Test
    void recent_boundedAt100Records() {
        TokenUsageStore store = new TokenUsageStore();
        for (int i = 0; i < 120; i++) {
            store.record("fabric", "req-" + i, new TokenUsage(1, 1, 2));
        }

        assertThat(store.recentCount()).isEqualTo(100);
        @SuppressWarnings("unchecked")
        List<TokenUsageStore.UsageRecord> recent =
                (List<TokenUsageStore.UsageRecord>) store.snapshot(PRICING).get("recent");
        assertThat(recent).hasSize(100);
        // FIFO 淘汰：最早的 req-0..19 被清掉，保留 req-20..119
        assertThat(recent.get(0).requestId()).isEqualTo("req-20");
        assertThat(recent.get(99).requestId()).isEqualTo("req-119");
    }
}
