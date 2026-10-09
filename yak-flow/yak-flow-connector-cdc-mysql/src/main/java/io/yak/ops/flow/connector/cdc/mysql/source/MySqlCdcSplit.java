package io.yak.ops.flow.connector.cdc.mysql.source;

import io.yak.ops.flow.api.source.SourceSplit;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.util.Objects;

/**
 * Phase 4 MySQL CDC 单表持续分片。
 *
 * @param table 捕获表
 * @author weifuwan
 * @since 2026-09-27
 */
public record MySqlCdcSplit(DataSourceTablePath table) implements SourceSplit {

    public MySqlCdcSplit {
        Objects.requireNonNull(table, "table must not be null");
    }

    @Override
    public String splitId() {
        return String.join(
                ".",
                table.database() == null ? "" : table.database(),
                table.schema() == null ? "" : table.schema(),
                table.table());
    }
}
