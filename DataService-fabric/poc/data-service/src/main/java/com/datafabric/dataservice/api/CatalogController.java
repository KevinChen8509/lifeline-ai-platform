package com.datafabric.dataservice.api;

import com.datafabric.dataservice.metadata.OpenMetadataClient;
import com.datafabric.dataservice.metadata.TableMetadata;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * W5 数据资源目录：把 OpenMetadata 表元数据整形为目录条目。
 *
 * FQN 三段式（source.database.table）拆出 source/table；domain 用静态映射
 * （PoC 三表，量大了换 OM 的 glossary/domain —— ADR-021 预留）。
 * OM 离线 → OpenMetadataClient 返回空列表，degraded=true 由前端标注，不阻断页面。
 */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private static final Map<String, String> DOMAIN_BY_TABLE = Map.of(
            "customer", "客户域",
            "orders", "订单域",
            "risk_tags", "风险域");

    private final OpenMetadataClient omClient;

    public CatalogController(OpenMetadataClient omClient) {
        this.omClient = omClient;
    }

    public record ColumnDto(String name, String description) {}

    public record TableDto(
            String fqn, String source, String database, String table,
            String description, List<ColumnDto> columns, String domain) {}

    public record CatalogResponse(List<TableDto> tables, boolean degraded) {}

    @GetMapping("/tables")
    public CatalogResponse tables() {
        List<TableMetadata> raw = omClient.fetchTables();
        List<TableDto> tables = raw.stream().map(CatalogController::toDto).toList();
        return new CatalogResponse(tables, raw.isEmpty());
    }

    private static TableDto toDto(TableMetadata t) {
        String[] parts = t.fqn().split("\\.");
        String table = parts[parts.length - 1];
        return new TableDto(
                t.fqn(),
                parts.length > 0 ? parts[0] : "",
                parts.length > 1 ? parts[1] : "",
                table,
                t.description(),
                t.columns().stream()
                        .map(c -> new ColumnDto(c.name(), c.description()))
                        .toList(),
                DOMAIN_BY_TABLE.getOrDefault(table, "其他"));
    }
}
