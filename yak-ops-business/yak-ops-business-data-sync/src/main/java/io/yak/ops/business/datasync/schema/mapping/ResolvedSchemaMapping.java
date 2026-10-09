package io.yak.ops.business.datasync.schema.mapping;

import io.yak.ops.business.datasync.schema.LogicalTable;
import java.util.List;
import java.util.Objects;

/**
 * Column Mapping 解析后的 Source / Target Logical Schema。
 *
 * @param sourceTable 按 Mapping 顺序投影、保留来源字段名的读取 Schema
 * @param targetTable 按相同位置重命名为目标字段名的写入 Schema
 * @param columns 规范化后的来源到目标映射
 * @author weifuwan
 * @since 2026-10-05
 */
public record ResolvedSchemaMapping(
        LogicalTable sourceTable, LogicalTable targetTable, List<SchemaColumnMapping> columns) {

    public ResolvedSchemaMapping {
        Objects.requireNonNull(sourceTable, "sourceTable must not be null");
        Objects.requireNonNull(targetTable, "targetTable must not be null");
        columns = List.copyOf(Objects.requireNonNull(columns, "columns must not be null"));
        if (columns.isEmpty()) throw new IllegalArgumentException("columns must not be empty");
    }
}
