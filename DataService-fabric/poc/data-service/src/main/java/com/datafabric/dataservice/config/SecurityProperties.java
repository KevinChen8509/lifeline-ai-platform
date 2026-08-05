package com.datafabric.dataservice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * B2 鉴权配置 - PoC 单一共享 API Key
 *
 * 生产环境换成 Keycloak / IDP：
 *   - 应用启动时拉 JWKS
 *   - OAuth2 Resource Server JWT 校验
 *   - 角色 claim → SecurityContext
 */
@ConfigurationProperties(prefix = "datafabric.security")
public record SecurityProperties(

        /** 共享 API Key（X-API-Key 请求头匹配此值才放行 /api/v1/**） */
        @NotBlank
        String apiKey,

        /** PoC 警告阈值：少于 16 字符启动时 warn */
        int minKeyLength
) {
    public SecurityProperties {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("datafabric.security.api-key 必须配置");
        }
        if (minKeyLength <= 0) {
            minKeyLength = 16;
        }
    }
}
