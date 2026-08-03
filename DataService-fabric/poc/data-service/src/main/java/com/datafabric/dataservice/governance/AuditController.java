package com.datafabric.dataservice.governance;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 审计查询端点（PoC 演示用）
 *
 * 生产环境审计表在 ClickHouse，这里用 LoggingAuditLogger 的 ring buffer
 * 让 dashboard 能即时看到最近 N 条访问，不必另接 ClickHouse。
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final LoggingAuditLogger logger;

    public AuditController(LoggingAuditLogger logger) {
        this.logger = logger;
    }

    @GetMapping("/recent")
    public Map<String, Object> recent(@RequestParam(defaultValue = "20") int n) {
        List<AuditEvent> events = logger.recent(Math.min(n, 200));
        return Map.of(
                "total", logger.size(),
                "events", events
        );
    }
}
