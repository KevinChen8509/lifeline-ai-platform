package com.datafabric.dataservice.service;

import java.time.Instant;
import java.util.List;

/**
 * W5/W6 数据服务市场：服务定义（目录条目）。
 *
 * 三种形态（type 区分，PoC 统一 record 简化前端渲染）：
 *   - builtin      平台内置服务（画像/指标/Agent 双路），method/pathTemplate 定位端点，
 *                  由既有 Controller 承载，市场只做目录与试用入口
 *   - table-query  自助发布的单表参数化查询（W5 核心）：目录表 + 列白名单 + 过滤参数
 *   - fusion       跨表融合查询（W6-A 核心）：主表（同 table-query 声明）+ joins 从表，
 *                  两段参数化查询，应用层按关联键组装嵌套 JSON
 *
 * 安全模型：allowedColumns / filters.column / joins.*.columns / joinColumn 在发布时
 * 对照 OpenMetadata 元数据校验（第一道闸），执行时再做标识符正则复检（第二道闸），
 * 值一律 PreparedStatement 绑定。parentColumn 须 ∈ 主表 allowedColumns。
 *
 * 对外开放：自助发布服务携带服务级 apiKey（仅可调用自身 /query，见 SecurityConfig 双通道）。
 * W6-B 运营化：apiKey 附带 KeyPolicy（状态/过期/限流）——吊销或过期的 key 即刻失效，
 * 每服务每分钟调用上限，注册表持久化到平台 H2 文件库（重启 Key 不变）。
 */
public record ServiceDefinition(
        String slug,
        String name,
        String description,
        String type,
        String method,
        String pathTemplate,
        String source,
        String table,
        List<String> allowedColumns,
        List<FilterSpec> filters,
        List<JoinSpec> joins,
        int defaultLimit,
        String apiKey,
        KeyPolicy keyPolicy,
        Instant createdAt) {

    public static final String TYPE_BUILTIN = "builtin";
    public static final String TYPE_TABLE_QUERY = "table-query";
    public static final String TYPE_FUSION = "fusion";

    /** key 生命周期策略（builtin 无 key → keyPolicy 为 null） */
    public record KeyPolicy(String status, Instant expiresAt, int rateLimitPerMin) {

        public static final String STATUS_ACTIVE = "ACTIVE";
        public static final String STATUS_REVOKED = "REVOKED";

        /** 吊销或已过期 → key 不可用 */
        public boolean isUsable() {
            return STATUS_ACTIVE.equals(status)
                    && (expiresAt == null || Instant.now().isBefore(expiresAt));
        }
    }

    /** 可配置过滤参数：列 × 操作符（eq/like/gte/lte），作用于主表 */
    public record FilterSpec(String column, String operator) {}

    /**
     * 融合从表声明（受限 DSL，不开放任意 SQL）：
     *   fqn            从表目录全限定名（列/joinColumn 须逐字命中其 OM 元数据）
     *   name           输出字段名（主行上的嵌套数组键，标识符正则）
     *   columns        从表返回列白名单
     *   joinColumn     从表关联列（如 orders.cust_id）
     *   parentColumn   主表关联列（须 ∈ 主表 allowedColumns，如 customer.cust_id）
     *   limitPerParent 每个主行的从行数上限（1..50）
     */
    public record JoinSpec(String fqn, String name, List<String> columns,
                           String joinColumn, String parentColumn, int limitPerParent) {}
}
