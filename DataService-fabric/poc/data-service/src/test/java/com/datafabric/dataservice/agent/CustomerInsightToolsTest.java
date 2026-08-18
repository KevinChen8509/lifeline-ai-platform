package com.datafabric.dataservice.agent;

import com.datafabric.dataservice.domain.CustomerProfileDto;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F7：列表类工具的精简投影——脱敏字段 / 时间戳不进 LLM 上下文
 */
class CustomerInsightToolsTest {

    @Test
    void slim_keepsBusinessFields_dropsMaskedAndNoiseFields() {
        CustomerProfileDto dto = new CustomerProfileDto(
                "C0001", "李娜", "138****0001", "110101********1234",
                "VIP3", "华东", 42L, 128000.0, "high", 88,
                "2023-01-15 10:30:00", "2026-07-20 14:00:00");

        Map<String, Object> slim = CustomerInsightTools.slim(dto);

        assertThat(slim).containsOnlyKeys(
                "custId", "custName", "customerLevel", "riskLevel",
                "region", "totalOrders", "totalAmount");
        assertThat(slim).containsEntry("custId", "C0001");
        assertThat(slim).containsEntry("riskLevel", "high");
        assertThat(slim).containsEntry("totalAmount", 128000.0);
        // 关键：脱敏后的 phone / idCard、治理隐藏的 riskScore、两个时间戳都不出现
        assertThat(slim.toString()).doesNotContain("138****", "110101", "88", "2023-01-15", "2026-07-20");
    }
}
