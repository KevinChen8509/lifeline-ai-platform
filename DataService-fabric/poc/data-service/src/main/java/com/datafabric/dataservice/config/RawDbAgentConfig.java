package com.datafabric.dataservice.config;

import com.datafabric.dataservice.agent.RawDbAgent;
import com.datafabric.dataservice.agent.RawDbTools;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public RawDbAgent rawDbAgent(ChatLanguageModel model, RawDbTools tools) {
        return AiServices.builder(RawDbAgent.class)
                .chatLanguageModel(model)
                .tools(tools)
                .build();
    }
}
