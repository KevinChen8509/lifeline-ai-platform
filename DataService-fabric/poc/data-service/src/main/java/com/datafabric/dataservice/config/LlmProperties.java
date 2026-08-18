package com.datafabric.dataservice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * W3 LLM 配置 - 火山方舟 GLM（OpenAI 兼容协议）
 *
 * 默认走火山方舟北京区：
 *   baseUrl = https://ark.cn-beijing.volces.com/api/v3
 *   model   = 用户在方舟控制台创建的 endpoint id（如 ep-20260xxx-xxxxx）
 *
 * 也支持任意 OpenAI-兼容服务（OpenAI / DeepSeek / 阿里百炼 / Ollama 等）。
 */
@ConfigurationProperties(prefix = "datafabric.llm")
public record LlmProperties(

        /** OpenAI-兼容的 base URL（火山方舟默认北京区） */
        @NotBlank
        String baseUrl,

        /** API Key（Bearer token） */
        @NotBlank
        String apiKey,

        /** 模型名或 endpoint id（火山方舟用 ep-xxx） */
        @NotBlank
        String model,

        /** 温度（0 = 确定性最强；PoC 默认 0.3） */
        Double temperature,

        /** 最大 token 数（默认 2048，足够 5 题回答） */
        Integer maxTokens,

        /** 超时秒（默认 30） */
        Integer timeoutSeconds,

        /** F6 输入 token 估算单价（元 / 百万 token，默认 2.0） */
        Double inputPricePerMillion,

        /** F6 输出 token 估算单价（元 / 百万 token，默认 8.0） */
        Double outputPricePerMillion
) {
    public LlmProperties {
        if (temperature == null) temperature = 0.3;
        if (maxTokens == null) maxTokens = 2048;
        if (timeoutSeconds == null) timeoutSeconds = 30;
        if (inputPricePerMillion == null) inputPricePerMillion = 2.0;
        if (outputPricePerMillion == null) outputPricePerMillion = 8.0;
    }
}
