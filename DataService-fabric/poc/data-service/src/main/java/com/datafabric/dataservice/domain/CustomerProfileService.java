package com.datafabric.dataservice.domain;

import com.datafabric.dataservice.client.CubeClient;
import com.datafabric.dataservice.client.CubeClient.CubeQuery;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 客户画像服务 - 调用 Cube.dev 语义层
 *
 * 业务逻辑统一从这里入口；治理（脱敏 / 审计 / 行级过滤）由 W2.3 的 AOP 切面切入，
 * 业务代码不感知治理策略，保持单一职责。
 */
@Service
public class CustomerProfileService {

    private static final String CUBE = "CustomerProfile.";
    private static final String METRICS = "CustomerMetrics.";

    private final CubeClient cube;

    public CustomerProfileService(CubeClient cube) {
        this.cube = cube;
    }

    /** 查单客户 - 跨源 JOIN 由 Cube 透明下推到 Trino */
    public Optional<CustomerProfileDto> findById(String custId) {
        List<Map<String, Object>> rows = cube.load(CubeQuery.builder()
                .dimension(CUBE + "custId")
                .dimension(CUBE + "custName")
                .dimension(CUBE + "phone")
                .dimension(CUBE + "idCard")
                .dimension(CUBE + "customerLevel")
                .dimension(CUBE + "region")
                .dimension(CUBE + "totalOrders")
                .dimension(CUBE + "riskLevel")
                .dimension(CUBE + "riskScore")
                .dimension(CUBE + "registerTime")
                .dimension(CUBE + "lastOrderTime")
                .measure(CUBE + "totalRevenue")
                .filter(CUBE + "custId", "equals", custId)
                .limit(1)
                .build());
        return rows.stream().findFirst().map(CustomerProfileService::toDto);
    }

    /** 客户分群 - 按等级过滤、分页 */
    public List<CustomerProfileDto> search(String level, int page, int size) {
        CubeQuery.Builder b = CubeQuery.builder()
                .dimension(CUBE + "custId")
                .dimension(CUBE + "custName")
                .dimension(CUBE + "customerLevel")
                .dimension(CUBE + "region")
                .dimension(CUBE + "totalOrders")
                .measure(CUBE + "totalRevenue")
                .order(CUBE + "totalRevenue", "desc")
                .limit(size)
                .offset(page * size);
        if (level != null && !level.isBlank()) {
            b.filter(CUBE + "customerLevel", "equals", level);
        }
        return cube.load(b.build()).stream()
                .map(CustomerProfileService::toDto)
                .toList();
    }

    /** 全局指标快照 - 从 CustomerMetrics cube 取衍生指标 */
    public CustomerOverviewDto overview() {
        List<Map<String, Object>> rows = cube.load(CubeQuery.builder()
                .measure(METRICS + "arpu")
                .measure(METRICS + "vipCustomerCount")
                .measure(METRICS + "highRiskCustomerCount")
                .measure(METRICS + "mediumRiskCustomerCount")
                .measure(METRICS + "lowRiskCustomerCount")
                .build());
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);
        return new CustomerOverviewDto(
                asDouble(row.get(METRICS + "arpu")),
                asInt(row.get(METRICS + "vipCustomerCount")),
                asInt(row.get(METRICS + "highRiskCustomerCount")),
                asInt(row.get(METRICS + "mediumRiskCustomerCount")),
                asInt(row.get(METRICS + "lowRiskCustomerCount"))
        );
    }

    private static CustomerProfileDto toDto(Map<String, Object> row) {
        return new CustomerProfileDto(
                asString(row.get(CUBE + "custId")),
                asString(row.get(CUBE + "custName")),
                asString(row.get(CUBE + "phone")),
                asString(row.get(CUBE + "idCard")),
                asString(row.get(CUBE + "customerLevel")),
                asString(row.get(CUBE + "region")),
                asLong(row.get(CUBE + "totalOrders")),
                asDouble(row.get(CUBE + "totalRevenue")),
                asString(row.get(CUBE + "riskLevel")),
                asInt(row.get(CUBE + "riskScore")),
                asString(row.get(CUBE + "registerTime")),
                asString(row.get(CUBE + "lastOrderTime"))
        );
    }

    // ---- 类型转换辅助 ----
    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static Double asDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
