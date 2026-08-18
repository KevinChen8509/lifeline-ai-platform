package com.datafabric.dataservice.config;

import com.datafabric.dataservice.agent.CustomerInsightAgent;
import com.datafabric.dataservice.agent.CustomerInsightStreamAgent;
import com.datafabric.dataservice.agent.CustomerInsightTools;
import com.datafabric.dataservice.agent.ToolRegistrations;
import com.datafabric.dataservice.observability.SyncTokenListener;
import com.datafabric.dataservice.observability.TokenUsageStore;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
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
 *  - ChatModel: OpenAI-兼容客户端（火山方舟 GLM 默认）
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
    public ChatModel chatModel(LlmProperties props, TokenUsageStore tokenUsageStore) {
        log.info("LLM 初始化: baseUrl={}, model={}, temperature={}, maxTokens={}",
                props.baseUrl(), props.model(), props.temperature(), props.maxTokens());
        return OpenAiChatModel.builder()
                .baseUrl(props.baseUrl())
                .apiKey(props.apiKey())
                .modelName(props.model())
                .temperature(props.temperature())
                .maxTokens(props.maxTokens())
                .timeout(Duration.ofSeconds(props.timeoutSeconds()))
                // F6: 同步路径用量采集（与 answer() 同线程，可读 UsageContext）
                .listeners(new SyncTokenListener(tokenUsageStore))
                .build();
    }

    @Bean
    public CustomerInsightAgent customerInsightAgent(
            ChatModel model,
            CustomerInsightTools tools,
            RestClient dataServiceRestClient) {
        Map<ToolSpecification, ToolExecutor> toolMap = ToolRegistrations.buildToolMap(tools);
        log.info("CustomerInsightAgent 注册 {} 个工具（已包装 SanitizingToolExecutor）", toolMap.size());
        return AiServices.builder(CustomerInsightAgent.class)
                .chatModel(model)
                .tools(toolMap)
                .build();
    }

    /**
     * F5 流式模型 bean（独立于 {@link #chatModel}）。
     *
     * 流式与同步分别构造，原因：OpenAI 兼容协议 stream=true 与 stream=false 是两个 HTTP 路径，
     * 同一个 model 实例不应承担两种角色。
     */
    @Bean
    public StreamingChatModel streamingChatModel(LlmProperties props) {
        log.info("LLM 流式初始化: baseUrl={}, model={}", props.baseUrl(), props.model());
        return OpenAiStreamingChatModel.builder()
                .baseUrl(props.baseUrl())
                .apiKey(props.apiKey())
                .modelName(props.model())
                .temperature(props.temperature())
                .maxTokens(props.maxTokens())
                .timeout(Duration.ofSeconds(props.timeoutSeconds()))
                .build();
    }

    /**
     * F5 流式版 A 路 agent。
     * 复用 CustomerInsightTools（同一份治理三切面），返回 TokenStream。
     */
    @Bean
    public CustomerInsightStreamAgent customerInsightStreamAgent(
            StreamingChatModel streamingModel,
            CustomerInsightTools tools,
            RestClient dataServiceRestClient) {
        Map<ToolSpecification, ToolExecutor> toolMap = ToolRegistrations.buildToolMap(tools);
        log.info("CustomerInsightStreamAgent 注册 {} 个工具（已包装 SanitizingToolExecutor）", toolMap.size());
        return AiServices.builder(CustomerInsightStreamAgent.class)
                .streamingChatModel(streamingModel)
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

