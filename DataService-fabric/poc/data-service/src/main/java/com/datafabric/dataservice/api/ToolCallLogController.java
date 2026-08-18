package com.datafabric.dataservice.api;

import com.datafabric.dataservice.observability.ToolCallLogger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * F2 工具调用日志查询端点
 *
 *   GET /api/v1/agent/tool-calls?path=fabric&limit=50
 *
 * 返回按 tool 聚合（次数 / 失败 / 平均耗时 / 最大耗时）+ 最近调用明细
 * （最新在前）。结构化日志本身走 logger TOOL_CALL_LOG（可接 Loki / CH）。
 */
@RestController
@RequestMapping("/api/v1/agent")
public class ToolCallLogController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final ToolCallLogger toolCallLogger;

    public ToolCallLogController(ToolCallLogger toolCallLogger) {
        this.toolCallLogger = toolCallLogger;
    }

    @GetMapping("/tool-calls")
    public Map<String, Object> toolCalls(
            @RequestParam(required = false) String path,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        if (path != null && !"fabric".equals(path) && !"raw".equals(path)) {
            throw new IllegalArgumentException("path 只支持 fabric 或 raw");
        }
        int bounded = Math.max(1, Math.min(limit, MAX_LIMIT));
        Map<String, Object> snapshot = toolCallLogger.snapshot(path, bounded);
        snapshot.put("filter", Map.of(
                "path", path == null ? "all" : path,
                "limit", bounded));
        return snapshot;
    }
}
