package com.datafabric.dataservice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * W6-B 注册表持久化库配置 —— 平台自有存储（默认 H2 文件库），与三源数据池
 * （datafabric.raw-db）刻意分离：半 live 栈的 "mysql" 汐是 H2 内存替身（重启即失），
 * 混用会架空 "发布的服务与 API Key 重启不丢" 这一持久化目标。
 */
@ConfigurationProperties(prefix = "datafabric.registry-db")
public record RegistryDbProperties(

        @NotBlank String url, String user, String password
) {
}
