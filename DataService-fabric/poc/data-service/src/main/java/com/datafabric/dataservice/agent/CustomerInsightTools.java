package com.datafabric.dataservice.agent;

import com.datafabric.dataservice.domain.CustomerOverviewDto;
import com.datafabric.dataservice.domain.CustomerProfileDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * W3 CustomerInsightAgent 工具集（A 路）
 *
 * 4 个 @Tool 全部通过 RestClient 调本服务 /api/v1/*，治理三切面（脱敏/审计/血缘）
 * 会正常触发。返回 JSON 字符串给 LLM。
 *
 * Tool 注册到 LangChain4j 后，LLM 根据用户问题自动选择调用哪个工具：
 *   "C0001 画像是什么"      → getCustomerProfile
 *   "VIP3 客户有多少"       → searchCustomersByLevel + 客户端 count
 *   "全局指标"              → getCustomerMetrics
 *   "高风险客户列表"        → getHighRiskCustomers
 */
@Component
public class CustomerInsightTools {

    private static final Logger log = LoggerFactory.getLogger(CustomerInsightTools.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient client;

    public CustomerInsightTools(RestClient dataServiceRestClient) {
        this.client = dataServiceRestClient;
    }

    @Tool("根据客户 ID 查询单个客户的完整画像，包括姓名、等级、地区、订单数、总消费、风险等级、风险分。")
    public String getCustomerProfile(@P("客户 ID，例如 C0001") String custId) {
        log.info("Tool[getCustomerProfile] custId={}", custId);
        try {
            CustomerProfileDto dto = client.get()
                    .uri("/api/v1/customers/{id}/profile", custId)
                    .retrieve()
                    .body(CustomerProfileDto.class);
            return JSON.writeValueAsString(dto);
        } catch (Exception e) {
            return "ERROR: " + e.getClass().getSimpleName() + " / " + sanitize(e.getMessage());
        }
    }

    @Tool("按客户等级（如 VIP1/VIP2/VIP3）分页查询客户列表。")
    public String searchCustomersByLevel(
            @P("客户等级，如 VIP1、VIP2、VIP3") String level,
            @P("页码，从 0 开始") int page,
            @P("每页大小，1-200") int size) {
        log.info("Tool[searchCustomersByLevel] level={} page={} size={}", level, page, size);
        try {
            CustomerProfileDto[] arr = client.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/customers")
                            .queryParam("level", level)
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .body(CustomerProfileDto[].class);
            List<CustomerProfileDto> list = arr == null ? List.of() : Arrays.asList(arr);
            return JSON.writeValueAsString(Map.of(
                    "count", list.size(),
                    "customers", list));
        } catch (Exception e) {
            return "ERROR: " + e.getClass().getSimpleName() + " / " + sanitize(e.getMessage());
        }
    }

    @Tool("获取全局客户指标快照：ARPU、VIP 客户数、高风险/中风险/低风险客户数。")
    public String getCustomerMetrics() {
        log.info("Tool[getCustomerMetrics]");
        try {
            CustomerOverviewDto dto = client.get()
                    .uri("/api/v1/metrics/customer-overview")
                    .retrieve()
                    .body(CustomerOverviewDto.class);
            return JSON.writeValueAsString(dto);
        } catch (Exception e) {
            return "ERROR: " + e.getClass().getSimpleName() + " / " + sanitize(e.getMessage());
        }
    }

    @Tool("获取全部高风险客户列表（riskLevel=high）。")
    public String getHighRiskCustomers() {
        log.info("Tool[getHighRiskCustomers]");
        try {
            CustomerProfileDto[] arr = client.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/customers")
                            .queryParam("size", 200)
                            .build())
                    .retrieve()
                    .body(CustomerProfileDto[].class);
            List<CustomerProfileDto> high = arr == null ? List.of() :
                    Arrays.stream(arr).filter(d -> "high".equalsIgnoreCase(d.riskLevel())).toList();
            return JSON.writeValueAsString(Map.of(
                    "count", high.size(),
                    "highRiskCustomers", high));
        } catch (Exception e) {
            return "ERROR: " + e.getClass().getSimpleName() + " / " + sanitize(e.getMessage());
        }
    }

    /** B4 风格：错误消息可能含内部 URL，过滤掉 */
    private static String sanitize(String msg) {
        if (msg == null) return "";
        return msg.replaceAll("https?://[^\\s\"]+", "[url]");
    }
}
