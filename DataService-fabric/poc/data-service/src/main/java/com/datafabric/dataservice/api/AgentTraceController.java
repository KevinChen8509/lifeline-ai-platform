package com.datafabric.dataservice.api;

import com.datafabric.dataservice.governance.TraceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * F9 Agent trace 追溯端点
 *
 *   GET /api/v1/agent/trace/{requestId}
 *
 * 返回一次 Agent 请求的完整治理链路时间线：
 *   REQUEST（开始/结束）→ TOOL_CALL（LLM 工具调用）→ AUDIT（审计）→ LINEAGE（血缘）
 *
 * 数据来源 {@link TraceStore}（PoC 内存，最多 100 个请求）。
 * requestId 来自 Agent 端点响应（同步 JSON / SSE done 事件）。
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentTraceController {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceController.class);

    private final TraceStore traceStore;

    public AgentTraceController(TraceStore traceStore) {
        this.traceStore = traceStore;
    }

    @GetMapping("/trace/{requestId}")
    public ResponseEntity<Map<String, Object>> trace(@PathVariable String requestId) {
        Map<String, Object> trace = traceStore.get(requestId);
        if (trace == null) {
            // requestId 客户端自选，可能已被 FIFO 淘汰或不存在
            log.info("trace not found requestId={}", requestId);
            return ResponseEntity.status(404).body(Map.of(
                    "error", "TRACE_NOT_FOUND",
                    "message", "trace 不存在或已淘汰（内存仅保留最近 100 条）"));
        }
        return ResponseEntity.ok(trace);
    }
}
