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
 *   - aggregate    分组聚合查询（W6-C 核心）：allowedColumns = GROUP BY 维度 + aggregates
 *                  聚合列声明（SUM/COUNT/AVG/MIN/MAX），单条 GROUP BY SQL，输出维度+别名
 *   - agg-fusion   组合形态（W6-G）：主表 GROUP BY（维度=allowedColumns）+ joins 从表挂载 ——
 *                  主行 = 维度 + 聚合别名 + 嵌套从行；parentColumn 必是维度（挂载语义）
 *
 * 安全模型：allowedColumns / filters.column / joins.*.columns / joinColumn 在发布时
 * 对照 OpenMetadata 元数据校验（第一道闸），执行时再做标识符正则复检（第二道闸），
 * 值一律 PreparedStatement 绑定。parentColumn 须 ∈ 主表 allowedColumns。
 * aggregate 形态：filters.column 可为元数据任意列（WHERE 先于 GROUP BY），
 * aggregates 的 column 逐字命中元数据（COUNT 可空列 = COUNT(*)），alias 标识符唯一。
 *
 * W6-D 跨源融合：database（FQN 中段）随定义持久化，执行时 FROM 显式限定 库.表，
 * 标识符引号按源选方言（postgres 双引号，mysql/clickhouse 反引号）——
 * 从表可跨源（attachJoins 按从表 FQN 前缀路由连接池，应用层关联合并）。
 *
 * 对外开放：自助发布服务携带服务级 apiKey（仅可调用自身 /query，见 SecurityConfig 双通道）。
 * W6-B 运营化：apiKey 附带 KeyPolicy（状态/过期/限流）——吊销或过期的 key 即刻失效，
 * 每服务每分钟调用上限，注册表持久化到平台 H2 文件库（重启 Key 不变）。
 *
 * production-audit Blocker 2：apiKeyHash 存的是 SHA-256 哈希（ServiceKeys.sha256Hex），
 * 明文仅在发布/轮换响应里一次性返回（见 ServiceMarketplaceController.KeyedServiceView），
 * @JsonIgnore 保证哈希不随目录/详情 JSON 透出；存量明文行由 JdbcRegistryStore 自愈迁移。
 */
public record ServiceDefinition(
        String slug,
        String name,
        String description,
        String type,
        String method,
        String pathTemplate,
        String source,
        String database,
        String table,
        List<String> allowedColumns,
        List<FilterSpec> filters,
        List<JoinSpec> joins,
        List<AggSpec> aggregates,
        int defaultLimit,
        @com.fasterxml.jackson.annotation.JsonIgnore String apiKeyHash,
        KeyPolicy keyPolicy,
        Instant createdAt) {

    public static final String TYPE_BUILTIN = "builtin";
    public static final String TYPE_TABLE_QUERY = "table-query";
    public static final String TYPE_FUSION = "fusion";
    public static final String TYPE_AGGREGATE = "aggregate";
    /** W6-G 组合形态：主表 GROUP BY（维度=allowedColumns）+ 从表按维度挂载 */
    public static final String TYPE_AGG_FUSION = "agg-fusion";

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
     *   columns        从表返回列白名单（行级模式）
     *   joinColumn     从表关联列（如 orders.cust_id）
     *   parentColumn   主表关联列（须 ∈ 主表 allowedColumns，如 customer.cust_id）
     *   limitPerParent 每个主行的从行数上限（1..50；聚合模式无意义，不参与执行）
     *   aggregates     从表聚合声明（W6-F 聚合模式）：按 joinColumn GROUP BY，
     *                  每个主键至多 1 行聚合结果挂回主行 —— columns 与 aggregates 二选一（XOR）
     */
    public record JoinSpec(String fqn, String name, List<String> columns,
                           String joinColumn, String parentColumn, int limitPerParent,
                           List<AggSpec> aggregates) {
        public JoinSpec {
            columns = columns == null ? List.of() : columns;           // 旧持久化 JSON 无 aggregates → 行级语义不变
            aggregates = aggregates == null ? List.of() : aggregates;
        }
    }

    /**
     * 聚合列声明（W6-C 受限 DSL，与 joins 互斥）：
     *   function   SUM / COUNT / AVG / MIN / MAX（白名单）
     *   column     聚合目标列（逐字命中主表 OM 元数据）；空仅 COUNT 允许 = COUNT(*)
     *   alias      输出列名（标识符正则 + 唯一 + 不与分组维度撞名）
     */
    public record AggSpec(String function, String column, String alias) {

        /** COUNT(*) 形态（column 为空）；@JsonIgnore 防止派生属性混入持久化 JSON */
        @com.fasterxml.jackson.annotation.JsonIgnore
        public boolean isStar() {
            return column == null || column.isBlank();
        }

        /** SQL 片段：SUM(`col`) / COUNT(*) —— 只由校验后的白名单值拼装 */
        public String sqlExpr() {
            return isStar() ? function + "(*)" : function + "(`" + column + "`)";
        }
    }
}
