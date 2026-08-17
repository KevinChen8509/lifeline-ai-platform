package com.datafabric.dataservice.config;

import com.datafabric.dataservice.agent.RawDbAgent;
import com.datafabric.dataservice.agent.RawDbStreamAgent;
import com.datafabric.dataservice.agent.RawDbTools;
import com.datafabric.dataservice.agent.ToolRegistrations;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * W3 RawDbAgent 配置（对照组）
 *
 * 故意单独成 config（不混进 LangChainConfig），方便生产禁用：
 *   @ConditionalOnProperty(name = "datafabric.poc.raw-db-agent", havingValue = "true")
 * 加了上面这行就能完全关掉对照演示。
 */
@Configuration
public class RawDbAgentConfig {

    @Bean
    public RawDbAgent rawDbAgent(ChatModel model, RawDbTools tools) {
        Map<ToolSpecification, ToolExecutor> toolMap = ToolRegistrations.buildToolMap(tools);
        LoggerFactory.getLogger(RawDbAgentConfig.class)
                .info("RawDbAgent 注册 {} 个工具（已包装 SanitizingToolExecutor）", toolMap.size());
        return AiServices.builder(RawDbAgent.class)
                .chatModel(model)
                .tools(toolMap)
                .build();
    }

    /**
     * F5 流式版 B 路 agent（对照组）。
     * 复用 RawDbTools（同一份直查表工具），返回 TokenStream。
     */
    @Bean
    public RawDbStreamAgent rawDbStreamAgent(StreamingChatModel streamingModel, RawDbTools tools) {
        Map<ToolSpecification, ToolExecutor> toolMap = ToolRegistrations.buildToolMap(tools);
        LoggerFactory.getLogger(RawDbAgentConfig.class)
                .info("RawDbStreamAgent 注册 {} 个工具（已包装 SanitizingToolExecutor）", toolMap.size());
        return AiServices.builder(RawDbStreamAgent.class)
                .streamingChatModel(streamingModel)
                .tools(toolMap)
                .build();
    }
}

