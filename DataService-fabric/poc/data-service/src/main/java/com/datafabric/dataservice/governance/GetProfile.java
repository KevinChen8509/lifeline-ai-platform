package com.datafabric.dataservice.governance;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 "查询客户画像" 的 controller 方法 —— AOP 切入点
 *
 * 三个 governance aspect 都基于此注解：
 *   - DataMaskingAspect  : 按角色脱敏 phone / idCard / riskScore
 *   - AuditAspect        : 记录每次访问到 ClickHouse audit_log
 *   - LineageAspect      : 发射 OpenLineage 事件
 *
 * 业务 controller 只加这一行注解，治理策略由 AOP 统一切入。
 * 改注解参数（view）能调整脱敏强度，不需要改业务代码。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GetProfile {

    /** 视图模式：full / brief / risk-only */
    String view() default "full";
}
