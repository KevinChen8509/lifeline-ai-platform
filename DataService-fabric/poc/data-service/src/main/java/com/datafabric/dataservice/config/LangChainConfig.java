package com.datafabric.dataservice.config;

import com.datafabric.dataservice.agent.CustomerInsightAgent;
import com.datafabric.dataservice.agent.CustomerInsightTools;
import com.datafabric.dataservice.agent.ToolRegistrations;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

import dev.langchain4j.agent.tool.ToolSpecification;

/**
 * W3 LangChain4j 配置
 *
 *  - ChatLanguageModel: OpenAI-兼容客户端（火山方舟 GLM 默认）
 *  - CustomerInsightAgent: AiServices.builder + 自定义 ToolExecutor map
 *    （SanitizingToolExecutor 兜底 GLM-4.7 残缺 arguments）
 *
 * RawDbAgent 不在这里注册（避免误用），由 RawDbAgentConfig 单独管理。
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LangChainConfig {

    private static final Logger log = LoggerFactory.getLogger(LangChainConfig.class);

    @Bean
    public ChatLanguageModel chatLanguageModel(LlmProperties props) {
        log.info("LLM 初始化: baseUrl={}, model={}, temperature={}, maxTokens={}",
                props.baseUrl(), props.model(), props.temperature(), props.maxTokens());
        return OpenAiChatModel.builder()
                .baseUrl(props.baseUrl())
                .apiKey(props.apiKey())
                .modelName(props.model())
                .temperature(props.temperature())
                .maxTokens(props.maxTokens())
                .timeout(Duration.ofSeconds(props.timeoutSeconds()))
                .build();
    }

    @Bean
    public CustomerInsightAgent customerInsightAgent(
            ChatLanguageModel model,
            CustomerInsightTools tools,
            RestClient dataServiceRestClient) {
        Map<ToolSpecification, ToolExecutor> toolMap = ToolRegistrations.buildToolMap(tools);
        log.info("CustomerInsightAgent 注册 {} 个工具（已包装 SanitizingToolExecutor）", toolMap.size());
        return AiServices.builder(CustomerInsightAgent.class)
                .chatLanguageModel(model)
                .tools(toolMap)
                .build();
    }

    /**
     * 自调用 RestClient（带 X-API-Key）— Tools 用它调本服务 /api/v1/*，
     * 这样治理三切面（脱敏/审计/血缘）会正常触发。
     */
    @Bean
    public RestClient dataServiceRestClient(
            RestClient.Builder builder,
            SecurityProperties security,
            CubeProperties cube) {
        // 同 JVM 自调，走 localhost:本服务端口
        return builder
                .baseUrl("http://localhost:8090")
                .defaultHeader(SecurityConfig.HEADER_API_KEY, security.apiKey())
                .build();
    }
}

