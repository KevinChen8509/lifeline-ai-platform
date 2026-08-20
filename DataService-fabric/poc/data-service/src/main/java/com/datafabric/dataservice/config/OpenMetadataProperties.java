package com.datafabric.dataservice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * F3 OpenMetadata 连接配置（A 路 RAG 上下文来源）。
 *
 * OpenMetadata 离线时 A 路自动降级为无 RAG 上下文（不阻断），见 MetadataContextService。
 */
@ConfigurationProperties(prefix = "datafabric.openmetadata")
public record OpenMetadataProperties(

        /** OpenMetadata API 根地址（含 /api 前缀，默认本地 Docker 端口） */
        @NotBlank String baseUrl,

        /** 可选 Bearer token（OM 开启认证时） */
        String token,

        /** RAG 总开关：false 时 augment() 原样透传，不发起任何 OM 请求 */
        boolean ragEnabled,

        /** 表元数据缓存 TTL（秒）；空结果同样缓存，防止离线时每请求重试 */
        long cacheTtlSeconds,

        /** 每次注入 prompt 的表数量上限 */
        int topK,

        /** 从 OM 拉取的表数量上限 */
        int maxTables
) {
}
