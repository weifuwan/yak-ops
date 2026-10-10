package io.yak.ops.business.datasync.impl;

import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.sql.Types;
import java.util.Comparator;
import java.util.List;

/** Constructs Core TableSchema test fixtures from existing physical-column test data. */
final class DataSyncTestTableSchema {

    private DataSyncTestTableSchema() {}

    static TableSchema fromColumns(List<DataSourceCatalogColumnVO> columns) {
        List<DataSourceCatalogColumnVO> ordered = columns.stream()
                .sorted(Comparator.comparingInt(column ->
                        column.getOrdinalPosition() == null ? Integer.MAX_VALUE : column.getOrdinalPosition()))
                .toList();
        List<Column> normalized = ordered.stream()
                .map(column -> new Column(
                        column.getName(),
                        column.getJdbcType() != null && column.getJdbcType() == Types.BIGINT
                                ? LogicalTypes.BIGINT
                                : LogicalTypes.STRING,
                        Boolean.TRUE.equals(column.getNullable()),
                        column.getJdbcType() != null && column.getJdbcType() == Types.BIGINT
                                ? null
                                : column.getSize()))
                .toList();
        List<String> primaryKeys = ordered.stream()
                .filter(column -> Boolean.TRUE.equals(column.getPrimaryKey()))
                .sorted(Comparator.comparingInt(column ->
                        column.getPrimaryKeyPosition() == null
                                ? Integer.MAX_VALUE
                                : column.getPrimaryKeyPosition()))
                .map(DataSourceCatalogColumnVO::getName)
                .toList();
        return new TableSchema(normalized, primaryKeys);
    }
}
