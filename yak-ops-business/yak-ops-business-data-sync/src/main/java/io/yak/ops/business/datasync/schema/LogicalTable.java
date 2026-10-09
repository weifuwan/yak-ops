package io.yak.ops.business.datasync.schema;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTableSchema;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Data Sync 产品层的逻辑表结构定义。
 *
 * <p>LogicalTable 是可持久化产品元数据的内存契约，不等同于 Datasource Catalog 的物理表，也不等同于
 * YakFlow Runtime 的 YakTableSchema。产品资源 ID、Workspace ownership 与审计字段由后续持久化层承载。
 *
 * @param name 逻辑表名称
 * @param comment 逻辑表业务备注；未提供时可为 null
 * @param schemaVersion 逻辑结构版本，从 1 开始
 * @param columns 按稳定逻辑顺序排列的字段
 * @param primaryKeys 按主键顺序排列的字段名称；无主键时为空列表
 * @author weifuwan
 * @since 2026-10-04
 */
public record LogicalTable(
        String name, String comment, long schemaVersion, List<LogicalColumn> columns, List<String> primaryKeys) {

    public LogicalTable {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(columns, "columns must not be null");
        Objects.requireNonNull(primaryKeys, "primaryKeys must not be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be greater than 0");
        }

        columns = List.copyOf(columns);
        primaryKeys = List.copyOf(primaryKeys);

        if (columns.isEmpty()) {
            throw new IllegalArgumentException("columns must not be empty");
        }

        Set<String> columnNames = new LinkedHashSet<>();
        for (LogicalColumn column : columns) {
            Objects.requireNonNull(column, "columns must not contain null");
            if (!columnNames.add(column.name())) {
                throw new IllegalArgumentException("duplicate logical column: " + column.name());
            }
        }

        Set<String> keys = new LinkedHashSet<>();
        for (String primaryKey : primaryKeys) {
            if (primaryKey == null || primaryKey.isBlank()) {
                throw new IllegalArgumentException("primaryKeys must not contain blank values");
            }
            if (!keys.add(primaryKey)) {
                throw new IllegalArgumentException("duplicate primary key: " + primaryKey);
            }
            if (!columnNames.contains(primaryKey)) {
                throw new IllegalArgumentException("primary key column not found: " + primaryKey);
            }
        }
    }

    /**
     * 投影为 YakFlow Runtime Schema。
     *
     * <p>产品层的 comment / schemaVersion 不进入 Runtime；字段名称、逻辑类型、nullable、capacity 与主键顺序保持。
     *
     * @return YakFlow 运行时表结构
     */
    public YakTableSchema toRuntimeSchema() {
        List<YakColumn> runtimeColumns = columns.stream()
                .map(column -> new YakColumn(column.name(), column.dataType(), column.nullable(), column.length()))
                .toList();
        return new YakTableSchema(runtimeColumns, primaryKeys);
    }
}
