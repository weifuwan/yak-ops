package io.yak.ops.business.datasync.schema.catalog;

import io.yak.ops.business.datasync.catalog.DataSyncCatalogColumns;
import io.yak.ops.business.datasync.schema.LogicalColumn;
import io.yak.ops.business.datasync.schema.LogicalTable;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.plugin.database.jdbc.schema.JdbcSchemaMapper;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 将 Datasource Catalog 物理元数据归一为 Data Sync 产品级 LogicalTable。
 *
 * <p>类型转换唯一复用 JdbcSchemaMapper，不在 Data Sync 复制 JDBC type switch。
 *
 * @author weifuwan
 * @since 2026-10-04
 */
public final class LogicalTableNormalizer {

    private LogicalTableNormalizer() {}

    /**
     * 将一张物理表及其字段元数据归一为初始 LogicalTable。
     *
     * @param table 物理表元数据
     * @param columns 物理字段元数据
     * @return schemaVersion=1 的逻辑表
     */
    public static LogicalTable fromCatalog(DataSourceCatalogTableVO table, List<DataSourceCatalogColumnVO> columns) {
        Objects.requireNonNull(table, "table must not be null");
        Objects.requireNonNull(columns, "columns must not be null");

        if (StringUtils.isBlank(table.getName())) {
            throw new IllegalArgumentException("catalog table name must not be blank");
        }
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("catalog columns must not be empty");
        }

        List<DataSourceCatalogColumnVO> orderedColumns = columns.stream()
                .sorted(Comparator.comparingInt(LogicalTableNormalizer::ordinal))
                .toList();
        List<DataSourceColumn> jdbcColumns = new ArrayList<>(orderedColumns.size());
        List<LogicalColumn> logicalColumns = new ArrayList<>(orderedColumns.size());

        for (int index = 0; index < orderedColumns.size(); index++) {
            DataSourceCatalogColumnVO source = orderedColumns.get(index);
            DataSourceColumn catalogColumn = DataSyncCatalogColumns.toColumn(source, index + 1);
            if (catalogColumn == null) {
                throw new IllegalArgumentException("catalog column metadata is incomplete");
            }

            var runtimeColumn = JdbcSchemaMapper.toYakColumn(catalogColumn);
            jdbcColumns.add(catalogColumn);
            logicalColumns.add(new LogicalColumn(
                    runtimeColumn.name(),
                    runtimeColumn.dataType(),
                    runtimeColumn.nullable(),
                    runtimeColumn.length(),
                    StringUtils.trimToNull(source.getRemarks())));
        }

        List<String> primaryKeys = JdbcSchemaMapper.fromColumns(jdbcColumns).primaryKeys();
        return new LogicalTable(
                table.getName(), StringUtils.trimToNull(table.getRemarks()), 1, logicalColumns, primaryKeys);
    }

    private static int ordinal(DataSourceCatalogColumnVO column) {
        Integer value = column == null ? null : column.getOrdinalPosition();
        return value == null || value <= 0 ? Integer.MAX_VALUE : value;
    }
}
