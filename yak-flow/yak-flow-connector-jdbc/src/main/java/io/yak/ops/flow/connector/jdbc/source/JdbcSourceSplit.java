package io.yak.ops.flow.connector.jdbc.source;

import io.yak.ops.flow.api.source.SourceSplit;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.util.Objects;

/**
 * bounded JDBC 表读取分片；可表示整表读取或一个显式整数范围。
 *
 * @param table 当前分片读取的源表
 * @param splitColumn 数值分片字段；整表 split 时为空
 * @param lowerBoundInclusive 范围最小包含值；整表 split 时为空
 * @param upperBoundInclusive 范围最大包含值；整表 split 时为空
 * @author weifuwan
 * @since 2026-09-27
 */
public record JdbcSourceSplit(
        DataSourceTablePath table, String splitColumn, Long lowerBoundInclusive, Long upperBoundInclusive)
        implements SourceSplit {

    public JdbcSourceSplit(DataSourceTablePath table) {
        this(table, null, null, null);
    }

    public JdbcSourceSplit {
        Objects.requireNonNull(table, "table must not be null");
        boolean hasColumn = splitColumn != null;
        boolean hasLowerBound = lowerBoundInclusive != null;
        boolean hasUpperBound = upperBoundInclusive != null;
        if (hasColumn != hasLowerBound || hasColumn != hasUpperBound) {
            throw new IllegalArgumentException("range split column and bounds must be configured together");
        }
        if (hasColumn && splitColumn.isBlank()) {
            throw new IllegalArgumentException("splitColumn must not be blank");
        }
        if (hasColumn && lowerBoundInclusive > upperBoundInclusive) {
            throw new IllegalArgumentException("lowerBoundInclusive must not be greater than upperBoundInclusive");
        }
    }

    public boolean isRangeSplit() {
        return splitColumn != null;
    }

    @Override
    public String splitId() {
        String tableId = String.join(
                ".",
                table.database() == null ? "" : table.database(),
                table.schema() == null ? "" : table.schema(),
                table.table());
        if (!isRangeSplit()) return tableId;
        return tableId + "#" + splitColumn + "[" + lowerBoundInclusive + "," + upperBoundInclusive + "]";
    }
}
