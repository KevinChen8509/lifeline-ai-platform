package com.datafabric.dataservice.governance;

import com.datafabric.dataservice.domain.CustomerProfileDto;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
        String role = StubMetadataClient.currentRole();
        String view = getProfile.view();

        // B1 修复：search 路由返回 List<CustomerProfileDto>，也要逐个脱敏
        if (result instanceof List<?> list) {
            List<CustomerProfileDto> masked = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof CustomerProfileDto dto) {
                    masked.add(applyOne(dto, role, view));
                } else {
                    // 类型混合的 List：原样保留，避免误伤
                    maskedReturnOriginal(item, masked);
                }
            }
            return masked;
        }

        if (result instanceof CustomerProfileDto dto) {
            return applyOne(dto, role, view);
        }
        return result;
    }

    private CustomerProfileDto applyOne(CustomerProfileDto dto, String role, String view) {
        FieldPolicy policy = metadata.getPolicy(dto.custId(), role);
        if (log.isDebugEnabled()) {
            log.debug("脱敏 view={} role={} custId={} maskPhone={} maskIdCard={} hideRisk={}",
                    view, role, dto.custId(),
                    policy.maskPhone(), policy.maskIdCard(), policy.hideRiskScore());
        }
        return apply(dto, policy, view);
    }

    /** 混合类型 List 的兜底（极少见，主要是 PoC 防御） */
    @SuppressWarnings("unchecked")
    private static void maskedReturnOriginal(Object item, List<CustomerProfileDto> target) {
        if (item == null) return;
        // 只放过 CustomerProfileDto，其他类型跳过（不丢、不脱敏）
        // 如果出现其他类型，说明业务代码与治理切面契约不一致，应该报错
        log.warn("List 中包含非 CustomerProfileDto 类型 {}，跳过脱敏", item.getClass().getName());
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
