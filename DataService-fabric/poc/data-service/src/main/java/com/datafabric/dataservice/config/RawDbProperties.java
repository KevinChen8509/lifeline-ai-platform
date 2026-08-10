package com.datafabric.dataservice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * W3 RawDbAgent 数据源配置 - 三源直连 JDBC（对照组）
 *
 * 故意绕开治理：直接 JDBC 查 MySQL/ClickHouse/PostgreSQL，
 * 不走 /api/v1/*，不触发脱敏 / 审计 / 血缘。
 */
@ConfigurationProperties(prefix = "datafabric.raw-db")
public record RawDbProperties(

        @NotBlank String mysqlUrl, @NotBlank String mysqlUser, String mysqlPassword,
        @NotBlank String clickhouseUrl, @NotBlank String clickhouseUser, String clickhousePassword,
        @NotBlank String postgresUrl, @NotBlank String postgresUser, String postgresPassword
) {
}
