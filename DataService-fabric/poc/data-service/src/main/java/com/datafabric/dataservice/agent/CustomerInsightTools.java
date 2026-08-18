package com.datafabric.dataservice.agent;

import com.datafabric.dataservice.domain.CustomerOverviewDto;
import com.datafabric.dataservice.domain.CustomerProfileDto;
import com.datafabric.dataservice.governance.TraceContext;
import com.datafabric.dataservice.governance.TraceEvent;
import com.datafabric.dataservice.governance.TraceStore;
import com.datafabric.dataservice.observability.ToolCallLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * W3 CustomerInsightAgent 工具集（A 路）
 *
 * 4 个 @Tool 全部通过 RestClient 调本服务 /api/v1/*，治理三切面（脱敏/审计/血缘）
 * 会正常触发。返回 JSON 字符串给 LLM。
 *
 * F9：每次工具调用把 TraceContext 里的 requestId 经 X-Request-Id 头透传给自调
 * HTTP，审计/血缘切面据此回写 TraceStore；同时记录 TOOL_CALL 事件（工具名、参数、
 * 耗时、成败）。
 */
@Component
public class CustomerInsightTools {

    private static final Logger log = LoggerFactory.getLogger(CustomerInsightTools.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient client;
    private final TraceStore traceStore;
    private final ToolCallLogger toolCallLogger;

    public CustomerInsightTools(RestClient dataServiceRestClient, TraceStore traceStore,
                                ToolCallLogger toolCallLogger) {
        this.client = dataServiceRestClient;
        this.traceStore = traceStore;
        this.toolCallLogger = toolCallLogger;
    }

    @Tool("根据客户 ID 查询单个客户的完整画像，包括姓名、等级、地区、订单数、总消费、风险等级、风险分。")
    public String getCustomerProfile(@P("客户 ID，例如 C0001") String custId) {
        log.info("Tool[getCustomerProfile] custId={}", custId);
        long t0 = System.currentTimeMillis();
        try {
            CustomerProfileDto dto = withTrace(client.get()
                    .uri("/api/v1/customers/{id}/profile", custId))
                    .retrieve()
                    .body(CustomerProfileDto.class);
            recordToolCall("getCustomerProfile", Map.of("custId", custId),
                    System.currentTimeMillis() - t0, true, "rows=" + (dto == null ? 0 : 1));
            return JSON.writeValueAsString(dto);
        } catch (Exception e) {
            recordToolCall("getCustomerProfile", Map.of("custId", custId),
                    System.currentTimeMillis() - t0, false, "ERROR:" + e.getClass().getSimpleName());
            return "ERROR: " + e.getClass().getSimpleName() + " / " + sanitize(e.getMessage());
        }
    }

    @Tool("按客户等级（如 VIP1/VIP2/VIP3）分页查询客户列表。")
    public String searchCustomersByLevel(
            @P("客户等级，如 VIP1、VIP2、VIP3") String level,
            @P("页码，从 0 开始") int page,
            @P("每页大小，1-200") int size) {
        log.info("Tool[searchCustomersByLevel] level={} page={} size={}", level, page, size);
        long t0 = System.currentTimeMillis();
        try {
            CustomerProfileDto[] arr = withTrace(client.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/customers")
                            .queryParam("level", level)
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build()))
                    .retrieve()
                    .body(CustomerProfileDto[].class);
            List<CustomerProfileDto> list = arr == null ? List.of() : Arrays.asList(arr);
            recordToolCall("searchCustomersByLevel",
                    Map.of("level", level, "page", page, "size", size),
                    System.currentTimeMillis() - t0, true, "rows=" + list.size());
            return JSON.writeValueAsString(Map.of(
                    "count", list.size(),
                    "customers", list));
        } catch (Exception e) {
            recordToolCall("searchCustomersByLevel",
                    Map.of("level", level, "page", page, "size", size),
                    System.currentTimeMillis() - t0, false, "ERROR:" + e.getClass().getSimpleName());
            return "ERROR: " + e.getClass().getSimpleName() + " / " + sanitize(e.getMessage());
        }
    }

    @Tool("获取全局客户指标快照：ARPU、VIP 客户数、高风险/中风险/低风险客户数。")
    public String getCustomerMetrics() {
        log.info("Tool[getCustomerMetrics]");
        long t0 = System.currentTimeMillis();
        try {
            CustomerOverviewDto dto = withTrace(client.get()
                    .uri("/api/v1/metrics/customer-overview"))
                    .retrieve()
                    .body(CustomerOverviewDto.class);
            recordToolCall("getCustomerMetrics", Map.of(),
                    System.currentTimeMillis() - t0, true, "ok");
            return JSON.writeValueAsString(dto);
        } catch (Exception e) {
            recordToolCall("getCustomerMetrics", Map.of(),
                    System.currentTimeMillis() - t0, false, "ERROR:" + e.getClass().getSimpleName());
            return "ERROR: " + e.getClass().getSimpleName() + " / " + sanitize(e.getMessage());
        }
    }

    @Tool("获取全部高风险客户列表（riskLevel=high）。")
    public String getHighRiskCustomers() {
        log.info("Tool[getHighRiskCustomers]");
        long t0 = System.currentTimeMillis();
        try {
            CustomerProfileDto[] arr = withTrace(client.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/customers")
                            .queryParam("size", 200)
                            .build()))
                    .retrieve()
                    .body(CustomerProfileDto[].class);
            List<CustomerProfileDto> high = arr == null ? List.of() :
                    Arrays.stream(arr).filter(d -> "high".equalsIgnoreCase(d.riskLevel())).toList();
            recordToolCall("getHighRiskCustomers", Map.of(),
                    System.currentTimeMillis() - t0, true, "rows=" + high.size());
            return JSON.writeValueAsString(Map.of(
                    "count", high.size(),
                    "highRiskCustomers", high));
        } catch (Exception e) {
            recordToolCall("getHighRiskCustomers", Map.of(),
                    System.currentTimeMillis() - t0, false, "ERROR:" + e.getClass().getSimpleName());
            return "ERROR: " + e.getClass().getSimpleName() + " / " + sanitize(e.getMessage());
        }
    }

    /** F9：Agent 上下文时给自调请求附加 X-Request-Id（流式回调线程无 ThreadLocal，返回原 spec） */
    private RestClient.RequestHeadersSpec<?> withTrace(RestClient.RequestHeadersSpec<?> spec) {
        String requestId = TraceContext.currentRequestId();
        return requestId == null ? spec : spec.header(TraceContext.HEADER, requestId);
    }

    /**
     * F2：全局工具调用日志（结构化 + 聚合，无 requestId 也记，如流式回调线程）。
     * F9：requestId 存在时另记 TOOL_CALL 事件进 TraceStore（请求级时间线）。
     */
    private void recordToolCall(String tool, Map<String, Object> args,
                                long elapsedMs, boolean ok, String summary) {
        toolCallLogger.record("fabric", tool, args, elapsedMs, ok, summary);
        String requestId = TraceContext.currentRequestId();
        if (requestId == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>(args);
        payload.put("tool", tool);
        payload.put("elapsedMs", elapsedMs);
        payload.put("result", ok ? "SUCCESS" : "FAILED");
        traceStore.add(requestId, TraceEvent.of("TOOL_CALL", payload));
    }

    /** B4 风格：错误消息可能含内部 URL，过滤掉 */
    private static String sanitize(String msg) {
        if (msg == null) return "";
        return msg.replaceAll("https?://[^\\s\"]+", "[url]");
    }
}
