package com.datafabric.dataservice.governance;

/**
 * 元数据策略客户端
 *
 * 真实实现：调 OpenMetadata REST API 读取字段级标签（PII / SENSITIVE / PUBLIC）
 * PoC 实现：{@link StubMetadataClient} 按角色返回预设策略
 *
 * 业务消费方（DataMaskingAspect）只依赖此接口 —— 切换实现不影响业务。
 */
public interface MetadataClient {

    /**
     * 拿到某个业务对象的字段策略
     *
     * @param custId 客户 ID（PoC 不用，真实实现可能按客户等级返回不同策略）
     * @param role   当前调用者角色（"ADMIN" / "CUSTOMER_VIEWER" / "SUPPORT" / null）
     */
    FieldPolicy getPolicy(String custId, String role);
}
