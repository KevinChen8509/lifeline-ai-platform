package com.datafabric.dataservice.client;

import com.datafabric.dataservice.config.CubeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cube.dev REST 客户端
 *
 * 协议：POST /cubejs-api/v1/load
 *   Header: Authorization: Bearer <secret>
 *   Body:   {"query": { measures, dimensions, filters, ... }}
 *   Resp:   { "data": [ { "Cube.column": value, ... }, ... ], "annotation": {...} }
 *
 * 业务消费方不写 SQL，只声明要哪些 measures / dimensions / filters。
 */
@Component
public class CubeClient {

    private static final Logger log = LoggerFactory.getLogger(CubeClient.class);

    private final CubeProperties props;
    private final RestClient restClient;

    public CubeClient(CubeProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.apiSecret())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * 执行一次 Cube 查询，返回 data 数组（每行是 Map<"Cube.member", value>）。
     */
    public List<Map<String, Object>> load(CubeQuery query) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query.toRequestBody());

        if (log.isDebugEnabled()) {
            log.debug("Cube query: {}", body);
        }

        JsonNode resp = restClient.post()
                .uri("/cubejs-api/v1/load")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (resp == null || !resp.has("data")) {
            log.warn("Cube 响应无 data 字段: {}", resp);
            return List.of();
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode row : resp.get("data")) {
            Map<String, Object> r = new LinkedHashMap<>();
            row.fields().forEachRemaining(e -> r.put(e.getKey(), unwrap(e.getValue())));
            rows.add(r);
        }
        return rows;
    }

    private static Object unwrap(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isInt() || node.isLong()) return node.longValue();
        if (node.isDouble() || node.isFloat()) return node.doubleValue();
        if (node.isBoolean()) return node.booleanValue();
        return node.asText();
    }

    /**
     * Cube 查询构造器 — immutable，build() 后不可变。
     */
    public record CubeQuery(
            List<String> measures,
            List<String> dimensions,
            List<Filter> filters,
            List<Order> orders,
            Integer limit,
            Integer offset
    ) {
        public Map<String, Object> toRequestBody() {
            Map<String, Object> m = new LinkedHashMap<>();
            if (!measures.isEmpty()) m.put("measures", measures);
            if (!dimensions.isEmpty()) m.put("dimensions", dimensions);
            if (!filters.isEmpty()) {
                m.put("filters", filters.stream().map(Filter::toMap).toList());
            }
            if (!orders.isEmpty()) {
                Map<String, String> orderMap = new LinkedHashMap<>();
                for (Order o : orders) orderMap.put(o.member(), o.direction());
                m.put("order", orderMap);
            }
            if (limit != null) m.put("limit", limit);
            if (offset != null) m.put("offset", offset);
            return m;
        }

        public static Builder builder() {
            return new Builder();
        }

        public record Filter(String member, String operator, List<String> values) {
            Map<String, Object> toMap() {
                return Map.of(
                        "member", member,
                        "operator", operator,
                        "values", values
                );
            }
        }

        public record Order(String member, String direction) {}

        public static class Builder {
            private final List<String> measures = new ArrayList<>();
            private final List<String> dimensions = new ArrayList<>();
            private final List<Filter> filters = new ArrayList<>();
            private final List<Order> orders = new ArrayList<>();
            private Integer limit;
            private Integer offset;

            public Builder measure(String m) { measures.add(m); return this; }
            public Builder dimension(String d) { dimensions.add(d); return this; }
            public Builder filter(String member, String operator, String... values) {
                filters.add(new Filter(member, operator, Arrays.asList(values)));
                return this;
            }
            public Builder order(String member, String direction) {
                orders.add(new Order(member, direction));
                return this;
            }
            public Builder limit(int n) { this.limit = n; return this; }
            public Builder offset(int n) { this.offset = n; return this; }

            public CubeQuery build() {
                return new CubeQuery(
                        List.copyOf(measures),
                        List.copyOf(dimensions),
                        List.copyOf(filters),
                        List.copyOf(orders),
                        limit,
                        offset
                );
            }
        }
    }
}
