package com.datafabric.dataservice.governance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * PoC 实现 - SLF4J 结构化日志 + 内存 ring buffer（最近 200 条）
 *
 * 演示场景：调 /api/v1/audit/recent 看最近的访问记录
 * 生产场景：替换为 ClickHouseAuditLogger（HTTP 8123 INSERT），见 README
 */
@Component
public class LoggingAuditLogger implements AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(LoggingAuditLogger.class);
    private static final int BUFFER_SIZE = 200;

    private final Queue<AuditEvent> ringBuffer = new ConcurrentLinkedQueue<>();

    @Override
    public void log(AuditEvent event) {
        // 结构化日志，可以用 Filebeat / Loki 抓取
        log.info("AUDIT actor={} action={} resource={} risk={} result={}",
                event.actor(), event.action(), event.resource(),
                event.riskLevel(), event.result());

        ringBuffer.add(event);
        while (ringBuffer.size() > BUFFER_SIZE) {
            ringBuffer.poll();
        }
    }

    /** 最近 N 条审计记录（用于 /api/v1/audit/recent） */
    public List<AuditEvent> recent(int n) {
        List<AuditEvent> all = new ArrayList<>(ringBuffer);
        Collections.reverse(all);
        return all.subList(0, Math.min(n, all.size()));
    }

    /** 总累计条数（不含已淘汰） */
    public int size() {
        return ringBuffer.size();
    }
}
