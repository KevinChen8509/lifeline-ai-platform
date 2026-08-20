package com.datafabric.dataservice.metadata;

import java.util.List;

/**
 * F3 从 OpenMetadata 拉取的表元数据（表名 + 表注释 + 列注释），RAG 上下文的原料。
 */
public record TableMetadata(String fqn, String description, List<Column> columns) {

    public record Column(String name, String description) {}
}
