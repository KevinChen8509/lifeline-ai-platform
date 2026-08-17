package com.datafabric.dataservice.governance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F9 TraceStore 单元测试
 */
class TraceStoreTest {

    @Test
    void start_then_get_returns_request_metadata() {
        TraceStore store = new TraceStore();
        store.start("req-1", "fabric", "C0001 画像是什么");

        Map<String, Object> trace = store.get("req-1");

        assertThat(trace).isNotNull();
        assertThat(trace.get("requestId")).isEqualTo("req-1");
        assertThat(trace.get("path")).isEqualTo("fabric");
        assertThat(trace.get("question")).isEqualTo("C0001 画像是什么");
        assertThat((List<?>) trace.get("events")).isEmpty();
    }

    @Test
    void add_appends_events_in_order() {
        TraceStore store = new TraceStore();
        store.start("req-2", "raw", "VIP3 数量");

        store.add("req-2", TraceEvent.of("TOOL_CALL", Map.of("tool", "searchCustomersByLevel")));
        store.add("req-2", TraceEvent.of("AUDIT", Map.of("resource", "C0001")));
        store.add("req-2", TraceEvent.of("REQUEST", Map.of("status", "SUCCESS")));

        @SuppressWarnings("unchecked")
        List<TraceEvent> events = (List<TraceEvent>) store.get("req-2").get("events");

        assertThat(events).hasSize(3);
        assertThat(events.get(0).type()).isEqualTo("TOOL_CALL");
        assertThat(events.get(1).type()).isEqualTo("AUDIT");
        assertThat(events.get(2).type()).isEqualTo("REQUEST");
    }

    @Test
    void add_unknownRequestId_is_ignored() {
        TraceStore store = new TraceStore();
        store.add("ghost", TraceEvent.of("TOOL_CALL", Map.of()));

        assertThat(store.get("ghost")).isNull();
    }

    @Test
    void get_unknownRequestId_returns_null() {
        TraceStore store = new TraceStore();

        assertThat(store.get("nope")).isNull();
    }

    @Test
    void evict_keeps_at_most_100_requests() {
        TraceStore store = new TraceStore();
        for (int i = 0; i < 105; i++) {
            store.start("req-" + i, "fabric", "q" + i);
        }

        // 容量不变式（淘汰目标按 startedAt 取 min，紧凑循环下时间戳可能同值，
        // 不断言具体淘汰了哪个 id）
        assertThat(store.size()).isEqualTo(100);
        assertThat(store.get("req-104")).isNotNull(); // 最新必保留
    }
}
