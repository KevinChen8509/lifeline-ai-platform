package com.datafabric.dataservice.governance;

import com.datafabric.dataservice.domain.CustomerProfileDto;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 审计切面 - 每次 @GetProfile 方法成功返回后记录
 *
 * 注意：此切面在 {@link DataMaskingAspect}（@Around）之后执行，
 * 因此看到的 DTO 是已脱敏的（这是对的 —— 审计日志不应该包含明文）。
 *
 * 失败场景：方法抛异常时 AfterReturning 不触发，
 * 改用异常审计可在 @AfterThrowing 补充（PoC 暂不实现）。
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditLogger auditLogger;

    public AuditAspect(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @AfterReturning(
            pointcut = "@annotation(com.datafabric.dataservice.governance.GetProfile)",
            returning = "result"
    )
    public void record(JoinPoint jp, Object result) {
        String actor = currentActor();

        // B1 修复：search 返回 List<CustomerProfileDto>，每个 DTO 单独记一条审计
        if (result instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof CustomerProfileDto dto) {
                    logOne(actor, dto);
                }
            }
            return;
        }

        if (result instanceof CustomerProfileDto dto) {
            logOne(actor, dto);
        }
    }

    private void logOne(String actor, CustomerProfileDto dto) {
        AuditEvent event = AuditEvent.of(
                actor,
                "customer-profile.read",
                dto.custId(),
                dto.riskLevel(),
                "SUCCESS"
        );
        try {
            auditLogger.log(event);
        } catch (Exception e) {
            log.warn("审计日志写入失败（业务不阻塞）: {}", e.getMessage());
        }
    }

    private String currentActor() {
        String role = StubMetadataClient.currentRole();
        return role != null ? role : "ANONYMOUS";
    }
}
