package com.datafabric.dataservice.governance;

import com.datafabric.dataservice.domain.CustomerProfileDto;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 数据脱敏切面 - 拦截 @GetProfile 方法
 *
 * 设计要点：
 *   1. DTO 是 Java record（immutable），不能用 @AfterReturning + setter，
 *      必须用 @Around 拦截 proceed() 返回值，构造新的 masked DTO 返回
 *   2. 策略从 {@link MetadataClient} 拿，默认 StubMetadataClient 按角色给
 *   3. 角色从 HTTP header X-User-Role 读（PoC 简化），生产环境接 Keycloak
 *   4. view 模式驱动脱敏强度（brief 比 full 更严）
 *
 * 切入后效果：
 *   ADMIN 调 /api/v1/customers/C0001/profile → 看到 13800000001 + 完整身份证
 *   SUPPORT 调同样接口 → 看到 138****0001 + 110101********1234 + riskScore=null
 */
@Aspect
@Component
public class DataMaskingAspect {

    private static final Logger log = LoggerFactory.getLogger(DataMaskingAspect.class);

    private final MetadataClient metadata;

    public DataMaskingAspect(MetadataClient metadata) {
        this.metadata = metadata;
    }

    @Around("@annotation(getProfile)")
    public Object applyPolicy(ProceedingJoinPoint pjp, GetProfile getProfile) throws Throwable {
        Object result = pjp.proceed();
        if (!(result instanceof CustomerProfileDto dto)) {
            return result;
        }
        String role = StubMetadataClient.currentRole();
        FieldPolicy policy = metadata.getPolicy(dto.custId(), role);

        CustomerProfileDto masked = apply(dto, policy, getProfile.view());
        if (log.isDebugEnabled()) {
            log.debug("脱敏 view={} role={} custId={} maskPhone={} maskIdCard={} hideRisk={}",
                    getProfile.view(), role, dto.custId(),
                    policy.maskPhone(), policy.maskIdCard(), policy.hideRiskScore());
        }
        return masked;
    }

    private CustomerProfileDto apply(CustomerProfileDto o, FieldPolicy p, String view) {
        // brief 视图强制隐藏风险分和身份证，无论角色（最小披露原则）
        boolean briefMode = "brief".equalsIgnoreCase(view);
        boolean maskPhone = p.maskPhone();
        boolean maskIdCard = p.maskIdCard() || briefMode;
        boolean hideRisk = p.hideRiskScore() || briefMode;

        return new CustomerProfileDto(
                o.custId(),
                o.custName(),
                maskPhone ? MaskingUtil.phone(o.phone()) : o.phone(),
                maskIdCard ? MaskingUtil.idCard(o.idCard()) : o.idCard(),
                o.customerLevel(),
                o.region(),
                o.totalOrders(),
                o.totalAmount(),
                o.riskLevel(),
                hideRisk ? null : o.riskScore(),
                o.registerTime(),
                o.lastOrderTime()
        );
    }
}
