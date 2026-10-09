package io.yak.ops.flow.connector.jdbc;

import java.util.Objects;

/**
 * JDBC bounded Source 自动分片使用的表统计。
 *
 * @param splitColumn 可用于范围分片的单整数主键
 * @param lowerBound 当前最小主键值
 * @param upperBound 当前最大主键值
 * @param rowCount 当前统计行数
 * @author weifuwan
 * @since 2026-10-06
 */
public record JdbcSourceStatistics(String splitColumn, long lowerBound, long upperBound, long rowCount) {

    public JdbcSourceStatistics {
        Objects.requireNonNull(splitColumn, "splitColumn must not be null");
        if (splitColumn.isBlank()) {
            throw new IllegalArgumentException("splitColumn must not be blank");
        }
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException("lowerBound must not be greater than upperBound");
        }
        if (rowCount < 0) {
            throw new IllegalArgumentException("rowCount must not be negative");
        }
    }
}
