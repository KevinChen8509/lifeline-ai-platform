package com.datafabric.dataservice.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * F8 B 路三源连接池（HikariCP）
 *
 * 之前 RawDbTools 每次工具调用 DriverManager 裸建连接（TCP+认证 全额开销，
 * 且无重试）。F8 换 HikariCP：
 *   - 每源一个小池（PoC sizing：max 4 / min idle 1 / connectionTimeout 3s）
 *   - 懒启动（无参构造 + setter）：首个 getConnection 才启动池 / 解析驱动，
 *     应用启动零 JDBC 依赖（HikariConfig 构造会立刻启动池并触发
 *     ClickHouseDriver 静态初始化，DB 不在的环境直接起不来——踩坑）
 *   - initializationFailTimeout = -1：池启动后 DB 宕机也不算启动失败
 *   - destroyMethod=close：停机时池优雅关闭
 *
 * 连接池是基础设施，不是治理——B 路"无治理对照"不变（不脱敏/不审计/无血缘）。
 */
@Configuration
public class RawDbPoolConfig {

    private static final Logger log = LoggerFactory.getLogger(RawDbPoolConfig.class);

    private static final int MAX_POOL_SIZE = 4;
    private static final int MIN_IDLE = 1;
    private static final long CONNECTION_TIMEOUT_MS = 3_000;

    @Bean(destroyMethod = "close")
    public HikariDataSource mysqlPool(RawDbProperties props) {
        return build("raw-mysql", props.mysqlUrl(), props.mysqlUser(), props.mysqlPassword());
    }

    @Bean(destroyMethod = "close")
    public HikariDataSource clickhousePool(RawDbProperties props) {
        return build("raw-clickhouse", props.clickhouseUrl(), props.clickhouseUser(), props.clickhousePassword());
    }

    @Bean(destroyMethod = "close")
    public HikariDataSource postgresPool(RawDbProperties props) {
        return build("raw-postgres", props.postgresUrl(), props.postgresUser(), props.postgresPassword());
    }

    private static HikariDataSource build(String poolName, String url, String user, String password) {
        // 懒启动：无参构造 + setter，首个 getConnection() 才启动池并解析 JDBC 驱动。
        // （HikariConfig 构造会立即启动池 → DriverManager 解析驱动 → ClickHouseDriver
        // 静态初始化在无 DB 环境抛异常，连带应用起不来——F8 踩坑。）
        // 应用启动零 JDBC 依赖：测试 / CI / DB 不全在时照常 boot，监控端点报 NOT_STARTED。
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName(poolName);
        ds.setJdbcUrl(url);
        ds.setUsername(user);
        ds.setPassword(password);
        ds.setMaximumPoolSize(MAX_POOL_SIZE);
        ds.setMinimumIdle(MIN_IDLE);
        ds.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        // -1：即使池启动后 DB 不在，也不抛启动失败（后台继续尝试建连）
        ds.setInitializationFailTimeout(-1);
        log.info("HikariCP 池配置就绪（懒启动）: {} -> {}", poolName, url);
        return ds;
    }
}
