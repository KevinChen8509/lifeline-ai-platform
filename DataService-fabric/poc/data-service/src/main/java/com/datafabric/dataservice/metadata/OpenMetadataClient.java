package com.datafabric.dataservice.metadata;

import com.datafabric.dataservice.config.OpenMetadataProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * F3 OpenMetadata REST 客户端。
 *
 * 协议：GET /v1/tables?limit={max}&fields=columns
 *   Resp:  { "data": [ { "fullyQualifiedName": ..., "description": ...,
 *                        "columns": [ { "name":..., "description":... } ] } ] }
 *
 * 任何失败（离线/超时/5xx/解析错）返回空列表并 WARN 一次 —— RAG 是增强不是依赖，
 * OM 掉线不能拖垮 A 路（与 CubeClient 容错风格一致）。
 * 连接超时压到 1s：无 OM 环境的 CI/演示机上快速失败。
 */
@Component
@EnableConfigurationProperties(OpenMetadataProperties.class)
public class OpenMetadataClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMetadataClient.class);

    private final OpenMetadataProperties props;
    private final RestClient restClient;

    @Autowired
    public OpenMetadataClient(OpenMetadataProperties props) {
        this(props, buildRestClient(props));
    }

    /** 包内测试构造器：注入绑定了 MockRestServiceServer 的 RestClient */
    OpenMetadataClient(OpenMetadataProperties props, RestClient restClient) {
        this.props = props;
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(OpenMetadataProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(3));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory);
        if (props.token() != null && !props.token().isBlank()) {
            builder = builder.defaultHeader("Authorization", "Bearer " + props.token());
        }
        return builder.build();
    }

    /** 拉取表元数据；失败返回空列表（调用方负责缓存空结果避免重试风暴） */
    public List<TableMetadata> fetchTables() {
        try {
            JsonNode resp = restClient.get()
                    .uri(uri -> uri.path("/v1/tables")
                            .queryParam("limit", props.maxTables())
                            .queryParam("fields", "columns")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            return parse(resp);
        } catch (Exception e) {
            log.warn("OpenMetadata 不可用，RAG 上下文降级为空: {}", e.getMessage());
            return List.of();
        }
    }

    private static List<TableMetadata> parse(JsonNode resp) {
        List<TableMetadata> tables = new ArrayList<>();
        if (resp == null || !resp.has("data") || !resp.get("data").isArray()) {
            return tables;
        }
        for (JsonNode t : resp.get("data")) {
            List<TableMetadata.Column> columns = new ArrayList<>();
            if (t.has("columns") && t.get("columns").isArray()) {
                for (JsonNode c : t.get("columns")) {
                    columns.add(new TableMetadata.Column(
                            text(c, "name"), text(c, "description")));
                }
            }
            tables.add(new TableMetadata(
                    text(t, "fullyQualifiedName"), text(t, "description"), List.copyOf(columns)));
        }
        return tables;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText("");
    }
}
