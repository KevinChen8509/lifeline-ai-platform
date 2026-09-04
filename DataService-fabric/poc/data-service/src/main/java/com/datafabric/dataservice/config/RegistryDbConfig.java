package com.datafabric.dataservice.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * W6-B 注册表持久化连接池（镜像 RawDbPoolConfig 的懒启动模式，F8 踩坑结论）：
 *   - 无参构造 + setter：首个 getConnection 才启动池，应用启动零 JDBC 依赖
 *   - initializationFailTimeout = -1：库文件不可写/磁盘异常不炸 boot，
 *     JdbcRegistryStore 侧降级为内存注册表（WARN 日志）
 */
@Configuration
public class RegistryDbConfig {

    private static final Logger log = LoggerFactory.getLogger(RegistryDbConfig.class);

    @Bean(destroyMethod = "close")
    public HikariDataSource registryPool(RegistryDbProperties props) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("registry-db");
        ds.setJdbcUrl(props.url());
        ds.setUsername(props.user());
        ds.setPassword(props.password() == null ? "" : props.password());
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(1);
        ds.setConnectionTimeout(3_000);
        ds.setInitializationFailTimeout(-1);
        log.info("HikariCP 池配置就绪（懒启动）: registry-db -> {}", props.url());
        return ds;
    }
}
