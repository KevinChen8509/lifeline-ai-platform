package com.datafabric.dataservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Data Fabric 数据服务启动类
 *
 * 端口 8090，对外暴露：
 *   GET  /api/v1/customers/{custId}/profile    单客户画像（跨源 JOIN 透明）
 *   GET  /api/v1/customers                     客户分群查询
 *   GET  /api/v1/metrics/customer-overview     全局指标快照
 *
 * 不直接连 MySQL/ClickHouse/PostgreSQL，统一走 Cube.dev 语义层。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataServiceApplication.class, args);
    }
}
