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
 * 数据血缘切面 - 发射 OpenLineage 事件
 *
 * 设计 spec 期望：API 端点 → Cube CustomerProfile → 上游 MySQL/CH/CSV 三表
 *
 * PoC 实现：以结构化 JSON 输出到日志（生产可被 OpenMetadata collector 抓取）
 * 生产实现：注入 OpenLineageClient，emit(event) 到 OpenLineage HTTP endpoint
 *
 * F9：捕获 X-Request-Id 后同步写入 {@link TraceStore}。
 */
@Aspect
@Component
public class LineageAspect {

    private static final Logger log = LoggerFactory.getLogger(LineageAspect.class);

    /** 设计文档中的三源表（PoC 实际拓扑：csv 换成 postgres） */
    private static final List<String> UPSTREAM = List.of(
            "mysql.customer_db.customer",
            "clickhouse.analytics.orders",
            "postgres.external.risk_tags"
    );

    private final TraceStore traceStore;

    public LineageAspect(TraceStore traceStore) {
        this.traceStore = traceStore;
    }

    @AfterReturning(
            pointcut = "@annotation(com.datafabric.dataservice.governance.GetProfile)",
            returning = "result"
    )
    public void emit(JoinPoint jp, Object result) {
        // B1 修复：search 返回 List<CustomerProfileDto>，发一个聚合 lineage 事件
        if (result instanceof List<?> list) {
            List<String> custIds = list.stream()
                    .filter(item -> item instanceof CustomerProfileDto)
                    .map(item -> ((CustomerProfileDto) item).custId())
                    .toList();
            if (custIds.isEmpty()) return;
            log.info(
                    "OPENLINEAGE job=data-service profile.search count={} custIds={} runId={} inputs={} outputs=[data-service.CustomerProfile]",
                    custIds.size(), custIds, java.util.UUID.randomUUID(), UPSTREAM
            );
            addTrace("profile.search", "count=" + custIds.size(), custIds.size());
            return;
        }

        if (!(result instanceof CustomerProfileDto dto)) {
            return;
        }
        // OpenLineage RunEvent 简化版（生产用 openlineage-java.jar 序列化）
        log.info(
                "OPENLINEAGE job=data-service profile.read custId={} runId={} inputs={} outputs=[data-service.CustomerProfile]",
                dto.custId(),
                java.util.UUID.randomUUID(),
                UPSTREAM
        );
        addTrace("profile.read", dto.custId(), 1);
    }

    private void addTrace(String job, String resource, int count) {
        String requestId = currentRequestId();
        if (requestId == null) {
            return;
        }
        traceStore.add(requestId, TraceEvent.of("LINEAGE", Map.of(
                "job", job,
                "resource", resource,
                "count", count,
                "inputs", UPSTREAM,
                "outputs", List.of("data-service.CustomerProfile"))));
    }

    private String currentRequestId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getHeader(TraceContext.HEADER);
        }
        return null;
    }
}
