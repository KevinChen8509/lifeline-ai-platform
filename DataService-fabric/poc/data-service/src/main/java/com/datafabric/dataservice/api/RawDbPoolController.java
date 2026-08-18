package com.datafabric.dataservice.api;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * F8 B 路连接池监控端点
 *
 *   GET /api/v1/raw-db/pools
 *
 * 三源 HikariCP 池实时状态：active / idle / total / awaiting（等连接的线程数）。
 * awaiting > 0 或 active 长期顶格 = 池不够用的信号。
 */
@RestController
@RequestMapping("/api/v1/raw-db")
public class RawDbPoolController {

    private final HikariDataSource mysqlPool;
    private final HikariDataSource clickhousePool;
    private final HikariDataSource postgresPool;

    public RawDbPoolController(HikariDataSource mysqlPool,
                               HikariDataSource clickhousePool,
                               HikariDataSource postgresPool) {
        this.mysqlPool = mysqlPool;
        this.clickhousePool = clickhousePool;
        this.postgresPool = postgresPool;
    }

    @GetMapping("/pools")
    public Map<String, Object> pools() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mysql", stats(mysqlPool));
        out.put("clickhouse", stats(clickhousePool));
        out.put("postgres", stats(postgresPool));
        return out;
    }

    private static Map<String, Object> stats(HikariDataSource ds) {
        Map<String, Object> out = new LinkedHashMap<>();
        HikariPoolMXBean pool = ds.getHikariPoolMXBean();
        // HikariCP 7 的 MXBean 没暴露 state 枚举；用池对象是否就绪区分
        out.put("state", pool == null ? "NOT_STARTED" : "STARTED");
        if (pool != null) {
            out.put("active", pool.getActiveConnections());
            out.put("idle", pool.getIdleConnections());
            out.put("total", pool.getTotalConnections());
            out.put("awaiting", pool.getThreadsAwaitingConnection());
        }
        out.put("config", Map.of(
                "maxPoolSize", ds.getMaximumPoolSize(),
                "minIdle", ds.getMinimumIdle(),
                "connectionTimeoutMs", ds.getConnectionTimeout()));
        return out;
    }
}
