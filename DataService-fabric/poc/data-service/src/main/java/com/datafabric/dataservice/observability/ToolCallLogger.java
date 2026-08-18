package com.datafabric.dataservice.observability;

import com.datafabric.dataservice.governance.TraceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * F2 Agent 工具调用日志
 *
 * A/B 两路工具统一埋点（与治理无关的运维级遥测——B 路仍是"无治理对照"，
 * 这里不脱敏数据流、不写审计，只记调用行为本身）：
 *   - 单行 JSON 结构化日志（logger 名 TOOL_CALL_LOG，Loki / ClickHouse 友好）
 *   - 内存近期记录（容量 200，FIFO）+ 按 tool 聚合（次数 / 失败 / 耗时）
 *
 * requestId 解析：TraceContext（同步 A 路）→ UsageContext（B 路）→ null。
 * summary 只放行为摘要（rows=N / ERROR:xxx），绝不放返回行内容（B 路含 PII 明文）。
 */
@Component
public class ToolCallLogger {

    /** 独立 logger 名，方便 logback 按名路由到独立 appender */
    private static final Logger LOG = LoggerFactory.getLogger("TOOL_CALL_LOG");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_RECENT = 200;

    public record ToolCall(
            Instant timestamp,
            String path,
            String requestId,
            String tool,
            Map<String, Object> args,
            boolean ok,
            long elapsedMs,
            String summary) {}

    record ToolStat(long totalCalls, long failures, long totalElapsedMs, long maxElapsedMs) {

        static ToolStat zero() {
            return new ToolStat(0, 0, 0, 0);
        }

        ToolStat plus(boolean ok, long elapsedMs) {
            return new ToolStat(
                    totalCalls + 1,
                    failures + (ok ? 0 : 1),
                    totalElapsedMs + elapsedMs,
                    Math.max(maxElapsedMs, elapsedMs));
        }

        ToolStat plus(ToolStat other) {
            return new ToolStat(
                    totalCalls + other.totalCalls,
                    failures + other.failures,
                    totalElapsedMs + other.totalElapsedMs,
                    Math.max(maxElapsedMs, other.maxElapsedMs));
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("totalCalls", totalCalls);
            m.put("failures", failures);
            m.put("avgElapsedMs", totalCalls == 0 ? 0 : totalElapsedMs / totalCalls);
            m.put("maxElapsedMs", maxElapsedMs);
            return m;
        }
    }

    private final Deque<ToolCall> recent = new ArrayDeque<>();
    private final ConcurrentMap<String, ToolStat> byTool = new ConcurrentHashMap<>();

    /** 记一次工具调用：结构化日志 + 内存 ring buffer + 按 tool 聚合。 */
    public void record(String path, String tool, Map<String, Object> args,
                       long elapsedMs, boolean ok, String summary) {
        String requestId = resolveRequestId();
        ToolCall call = new ToolCall(Instant.now(), path, requestId, tool,
                Map.copyOf(args), ok, elapsedMs, summary);

        writeStructuredLog(call);

        synchronized (this) {
            recent.addLast(call);
            while (recent.size() > MAX_RECENT) {
                recent.removeFirst();
            }
        }
        byTool.merge(tool, ToolStat.zero().plus(ok, elapsedMs), ToolStat::plus);
    }

    /**
     * 快照：按 path 过滤（null = 全部）+ 最近 limit 条（最新在前）+ 按 tool 聚合。
     */
    public synchronized Map<String, Object> snapshot(String pathFilter, int limit) {
        List<ToolCall> filtered = new ArrayList<>();
        List<ToolCall> all = new ArrayList<>(recent);
        for (int i = all.size() - 1; i >= 0 && filtered.size() < limit; i--) {
            ToolCall c = all.get(i);
            if (pathFilter == null || pathFilter.equals(c.path())) {
                filtered.add(c);
            }
        }

        Map<String, Object> byToolMap = new LinkedHashMap<>();
        byTool.forEach((tool, stat) -> byToolMap.put(tool, stat.toMap()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("byTool", byToolMap);
        out.put("recent", filtered);
        out.put("recentCount", filtered.size());
        return out;
    }

    private static String resolveRequestId() {
        String requestId = TraceContext.currentRequestId();
        if (requestId == null) {
            requestId = UsageContext.currentRequestId();
        }
        return requestId;
    }

    private static void writeStructuredLog(ToolCall call) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("ts", call.timestamp().toString());
        line.put("path", call.path());
        line.put("requestId", call.requestId());
        line.put("tool", call.tool());
        line.put("args", call.args());
        line.put("ok", call.ok());
        line.put("elapsedMs", call.elapsedMs());
        line.put("summary", call.summary());
        try {
            LOG.info(JSON.writeValueAsString(line));
        } catch (Exception e) {
            // JSON 序列化失败不干扰业务；args 都是简单类型，理论不会发生
            LOG.info("TOOL_CALL tool={} path={} ok={} elapsedMs={} summary={}",
                    call.tool(), call.path(), call.ok(), call.elapsedMs(), call.summary());
        }
    }
}
