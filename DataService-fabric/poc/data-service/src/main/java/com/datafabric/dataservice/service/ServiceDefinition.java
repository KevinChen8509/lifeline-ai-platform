package com.datafabric.dataservice.service;

import java.time.Instant;
import java.util.List;

/**
 * W5 数据服务市场：服务定义（目录条目）。
 *
 * 两种形态（type 区分，PoC 统一 record 简化前端渲染）：
 *   - builtin      平台内置服务（画像/指标/Agent 双路），method/pathTemplate 定位端点，
 *                  由既有 Controller 承载，市场只做目录与试用入口
 *   - table-query  自助发布的参数化查询服务（W5 核心）：从目录表 + 列白名单 +
 *                  过滤参数一键生成，执行走 {@link api.ServiceMarketplaceController}
 *
 * 安全模型：allowedColumns / filters.column 在发布时对照 OpenMetadata 元数据校验
 * （第一道闸），执行时再做标识符正则复检（第二道闸），值一律 PreparedStatement 绑定。
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
        int defaultLimit,
        Instant createdAt) {

    public static final String TYPE_BUILTIN = "builtin";
    public static final String TYPE_TABLE_QUERY = "table-query";

    /** 可配置过滤参数：列 × 操作符（eq/like/gte/lte） */
    public record FilterSpec(String column, String operator) {}
}
