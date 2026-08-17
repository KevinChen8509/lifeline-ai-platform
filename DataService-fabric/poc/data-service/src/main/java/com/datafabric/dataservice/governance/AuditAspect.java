package com.datafabric.dataservice.governance;

import com.datafabric.dataservice.domain.CustomerProfileDto;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

/**
 * 审计切面 - 每次 @GetProfile 方法成功返回后记录
 *
 * 注意：此切面在 {@link DataMaskingAspect}（@Around）之后执行，
 * 因此看到的 DTO 是已脱敏的（这是对的 —— 审计日志不应该包含明文）。
 *
 * F9：捕获上游 Agent 透传的 X-Request-Id 头（无则 null，普通 API 调用），
 * 同时把事件写入 {@link TraceStore} 供 /api/v1/agent/trace/{requestId} 查询。
 *
 * 失败场景：方法抛异常时 AfterReturning 不触发，
 * 改用异常审计可在 @AfterThrowing 补充（PoC 暂不实现）。
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditLogger auditLogger;
    private final TraceStore traceStore;

    public AuditAspect(AuditLogger auditLogger, TraceStore traceStore) {
        this.auditLogger = auditLogger;
        this.traceStore = traceStore;
    }

    @AfterReturning(
            pointcut = "@annotation(com.datafabric.dataservice.governance.GetProfile)",
            returning = "result"
    )
    public void record(JoinPoint jp, Object result) {
        String actor = currentActor();
        String requestId = currentRequestId();

        // B1 修复：search 返回 List<CustomerProfileDto>，每个 DTO 单独记一条审计
        if (result instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof CustomerProfileDto dto) {
                    logOne(actor, requestId, dto);
                }
            }
            return;
        }

        if (result instanceof CustomerProfileDto dto) {
            logOne(actor, requestId, dto);
        }
    }

    private void logOne(String actor, String requestId, CustomerProfileDto dto) {
        AuditEvent event = AuditEvent.of(
                actor,
                "customer-profile.read",
                dto.custId(),
                dto.riskLevel(),
                "SUCCESS",
                requestId
        );
        try {
            auditLogger.log(event);
        } catch (Exception e) {
            log.warn("审计日志写入失败（业务不阻塞）: {}", e.getMessage());
        }
        if (requestId != null) {
            traceStore.add(requestId, TraceEvent.of("AUDIT", Map.of(
                    "actor", actor,
                    "resource", dto.custId(),
                    "risk", dto.riskLevel(),
                    "result", "SUCCESS")));
        }
    }

    private String currentActor() {
        String role = StubMetadataClient.currentRole();
        return role != null ? role : "ANONYMOUS";
    }

    /** 从当前 HTTP 请求头取 Agent 透传的 requestId；非 HTTP 上下文返回 null */
    private String currentRequestId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getHeader(TraceContext.HEADER);
        }
        return null;
    }
}
