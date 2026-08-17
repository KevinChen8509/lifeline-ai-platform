package com.datafabric.dataservice.governance;

import java.time.Instant;
import java.util.Map;

/**
 * F9 trace 时间线事件 - 一次 Agent 请求内的单个可观测动作
 *
 * type 取值：
 *   REQUEST   - Agent 请求开始/结束（question、path、elapsedMs）
 *   TOOL_CALL - LLM 发起的工具调用（tool、args、结果状态）
 *   AUDIT     - 审计切面触发（复用 {@link AuditEvent} 字段）
 *   LINEAGE   - 血缘切面触发（inputs、outputs）
 */
public record TraceEvent(
        Instant timestamp,
        String type,
        Map<String, Object> payload
) {
    public static TraceEvent of(String type, Map<String, Object> payload) {
        return new TraceEvent(Instant.now(), type, payload);
    }
}
