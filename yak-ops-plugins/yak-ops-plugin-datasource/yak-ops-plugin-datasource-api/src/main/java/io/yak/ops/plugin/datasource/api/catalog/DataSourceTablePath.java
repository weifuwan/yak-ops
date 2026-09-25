package io.yak.ops.plugin.datasource.api.catalog;

import io.yak.ops.common.util.StringUtils;

/**
 * Catalog 中表的完整定位信息。
 *
 * @param database 数据库名称
 * @param schema Schema 名称
 * @param table 表、视图、Collection 或 Index 名称
 * @author weifuwan
 * @since 2026-09-24
 */
public record DataSourceTablePath(String database, String schema, String table) {

    public DataSourceTablePath {
        if (StringUtils.isBlank(table)) throw new IllegalArgumentException("table 不能为空");
        table = table.trim();
    }
}
