package com.datafabric.dataservice.governance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * F9 trace 存储 - 按 requestId 聚合一次 Agent 请求的全部治理事件
 *
 * PoC 内存实现（最多 100 个 request，FIFO 淘汰）。
 * 生产实现：事件异步写 ClickHouse / OpenTelemetry，trace 查询走存储。
 *
 * 线程模型：AgentController（servlet 线程）写 REQUEST 事件；工具自调的
 * /api/v1/* 子请求（也是 servlet 线程）写 AUDIT/LINEAGE 事件—— requestId
 * 经 X-Request-Id 头跨线程传递，这里只做按 key 汇聚。
 */
@Component
public class TraceStore {

    private static final Logger log = LoggerFactory.getLogger(TraceStore.class);
    private static final int MAX_REQUESTS = 100;

    private record TraceRecord(
            String requestId,
            String path,
            String question,
            Instant startedAt,
            List<TraceEvent> events
    ) {
        TraceRecord {
            events = new ArrayList<>();
        }
    }

    private final ConcurrentMap<String, TraceRecord> traces = new ConcurrentHashMap<>();

    /** Agent 请求开始时登记（幂等：重复 requestId 只保留首个） */
    public void start(String requestId, String path, String question) {
        traces.computeIfAbsent(requestId,
                id -> new TraceRecord(id, path, question, Instant.now(), new ArrayList<>()));
        evictIfNeeded();
    }

    /** 追加事件；requestId 未登记时忽略（如无 Agent 上游的普通 API 调用） */
    public void add(String requestId, TraceEvent event) {
        TraceRecord record = traces.get(requestId);
        if (record == null) {
            return;
        }
        synchronized (record.events()) {
            record.events().add(event);
        }
    }

    /** 查询：未登记的 requestId 返回 null（→ 404） */
    public Map<String, Object> get(String requestId) {
        TraceRecord record = traces.get(requestId);
        if (record == null) {
            return null;
        }
        List<TraceEvent> snapshot;
        synchronized (record.events()) {
            snapshot = List.copyOf(record.events());
        }
        return Map.of(
                "requestId", record.requestId(),
                "path", record.path(),
                "question", record.question(),
                "startedAt", record.startedAt().toString(),
                "events", snapshot);
    }

    /** 当前保留的 trace 数（容量上限 100） */
    public int size() {
        return traces.size();
    }

    private void evictIfNeeded() {
        if (traces.size() <= MAX_REQUESTS) {
            return;
        }
        // 淘汰最早开始的记录
        traces.values().stream()
                .min((a, b) -> a.startedAt().compareTo(b.startedAt()))
                .ifPresent(oldest -> {
                    traces.remove(oldest.requestId());
                    log.debug("trace 淘汰 requestId={}", oldest.requestId());
                });
    }
}
