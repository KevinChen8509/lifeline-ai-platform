package com.datafabric.dataservice.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cube.dev 连接配置
 *
 * 在 application.yml 的 `cube.*` 段映射；环境变量优先级最高。
 */
@Validated
@ConfigurationProperties(prefix = "cube")
public record CubeProperties(
        @NotBlank String host,
        @Min(1) int port,
        @NotBlank String apiSecret,
        @Min(500) int timeoutMs
) {
    /** Cube REST 基址，例如 http://cube:4000 */
    public String baseUrl() {
        return "http://%s:%d".formatted(host, port);
    }
}
