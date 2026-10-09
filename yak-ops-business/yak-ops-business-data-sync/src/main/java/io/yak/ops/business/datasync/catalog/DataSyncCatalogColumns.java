package io.yak.ops.business.datasync.catalog;

import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 收口 Data Sync 对 Datasource Catalog 字段的同名索引和 YakFlow 字段投影。
 *
 * @author weifuwan
 * @since 2026-09-28
 */
public final class DataSyncCatalogColumns {

    private DataSyncCatalogColumns() {}

    public static Map<String, DataSourceCatalogColumnVO> indexByName(List<DataSourceCatalogColumnVO> columns) {
        Map<String, DataSourceCatalogColumnVO> result = new LinkedHashMap<>();
        for (DataSourceCatalogColumnVO column : columns) {
            if (column.getName() != null) {
                result.put(column.getName().toLowerCase(Locale.ROOT), column);
            }
        }
        return result;
    }

    public static DataSourceCatalogColumnVO findByName(
            Map<String, DataSourceCatalogColumnVO> columnsByName, String name) {
        return name == null ? null : columnsByName.get(name.toLowerCase(Locale.ROOT));
    }

    public static Set<String> primaryKeyNames(List<DataSourceCatalogColumnVO> columns) {
        return columns.stream()
                .filter(column -> Boolean.TRUE.equals(column.getPrimaryKey()))
                .map(DataSourceCatalogColumnVO::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public static DataSourceColumn toColumn(DataSourceCatalogColumnVO column) {
        int ordinal =
                column == null || column.getOrdinalPosition() == null ? Integer.MAX_VALUE : column.getOrdinalPosition();
        return toColumn(column, ordinal);
    }

    public static DataSourceColumn toColumn(DataSourceCatalogColumnVO column, int ordinal) {
        if (column == null || column.getJdbcType() == null || column.getName() == null) return null;
        return new DataSourceColumn(
                column.getName(),
                column.getTypeName(),
                column.getJdbcType(),
                column.getSize(),
                column.getScale(),
                Boolean.TRUE.equals(column.getNullable()),
                ordinal,
                Boolean.TRUE.equals(column.getPrimaryKey()),
                column.getPrimaryKeyPosition(),
                column.getRemarks());
    }
}
