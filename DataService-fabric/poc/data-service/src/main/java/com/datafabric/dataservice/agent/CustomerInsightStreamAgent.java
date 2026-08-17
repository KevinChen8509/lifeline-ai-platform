package com.datafabric.dataservice.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * F5 流式版 CustomerInsightAgent（A 路）。
 *
 * 与 {@link CustomerInsightAgent} 共享 system message + 工具集，仅返回类型不同：
 * 同步版返回 {@code String}（一次性），流式版返回 {@link TokenStream}（逐 token 回调）。
 *
 * 用 LangChain4j {@code StreamingChatModel} 驱动，配合 Spring MVC {@code SseEmitter}
 * 实现 server-sent events 端到端推流。
 */
public interface CustomerInsightStreamAgent {

    @SystemMessage("""
            你是客户洞察助手。可通过工具回答：
              - 客户画像（基础信息 + 行为 + 风险）
              - VIP 客户筛选与统计
              - 高风险客户预警
              - 全局指标（总数 / VIP / 高风险 / 中低风险）

            强约束：
              1. 必须使用提供的工具调用，不得凭空编造客户 ID、手机号、身份证、金额。
              2. 工具返回的数据已经是治理后的结果（PII 已脱敏），直接复述即可。
              3. 回答简洁，给出关键数字 + 简短结论，不超过 150 字。
            """)
    TokenStream answer(@UserMessage String question);
}
