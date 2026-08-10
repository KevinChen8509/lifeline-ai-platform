package com.datafabric.dataservice.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * W3 CustomerInsightAgent - A 路（Data Fabric 路线）
 *
 * 通过 LangChain4j AiServices.builder(...) 在 LangChainConfig 构造，
 * 自动把自然语言问题路由到 @Tool 方法。
 * 全部 @Tool 调本服务 /api/v1/*，触发治理三切面（脱敏/审计/血缘）。
 *
 * 期望对比效果：
 *   - 准确率 ≥ 4/5（语义层 + 工具组合，覆盖业务概念）
 *   - 安全事件 = 0（所有 PII 经 DataMaskingAspect 脱敏）
 */
public interface CustomerInsightAgent {

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
    String answer(@UserMessage String question);
}
