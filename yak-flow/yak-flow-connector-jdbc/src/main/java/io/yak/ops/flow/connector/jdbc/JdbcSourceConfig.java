package io.yak.ops.flow.connector.jdbc;

import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import java.util.Objects;

/**
 * bounded JDBC Source 的执行配置，直接引用 Datasource 已规范化连接和 Catalog 表路径。
 *
 * @param connection 已规范化的数据源连接
 * @param table 源表路径
 * @param schema 源表逻辑结构
 * @param fetchSize JDBC 游标 fetch size
 * @param readBatchSize 每次向 Runtime 输出的最大行数
 * @param timeoutSeconds 连接与查询超时秒数
 * @param splitConfig 显式数值范围分片配置；为空时不使用显式范围
 * @param splitSize 动态分片目标行数；为空时不自动分析 MIN/MAX/rowCount
 * @author weifuwan
 * @since 2026-09-27
 */
public record JdbcSourceConfig(
        DataSourceConnection connection,
        DataSourceTablePath table,
        YakTableSchema schema,
        int fetchSize,
        int readBatchSize,
        int timeoutSeconds,
        JdbcNumericSplitConfig splitConfig,
        Long splitSize) {

    private static final int DEFAULT_FETCH_SIZE = 500;
    private static final int DEFAULT_READ_BATCH_SIZE = 500;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    public JdbcSourceConfig {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(table, "table must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        if (fetchSize <= 0) throw new IllegalArgumentException("fetchSize must be greater than 0");
        if (readBatchSize <= 0) throw new IllegalArgumentException("readBatchSize must be greater than 0");
        if (timeoutSeconds <= 0) throw new IllegalArgumentException("timeoutSeconds must be greater than 0");
        if (splitConfig != null && splitSize != null) {
            throw new IllegalArgumentException(
                    "explicit splitConfig and dynamic splitSize cannot be configured together");
        }
        if (splitSize != null && splitSize <= 0) {
            throw new IllegalArgumentException("splitSize must be greater than 0");
        }
        validateSplitConfig(schema, splitConfig);
    }

    public JdbcSourceConfig(
            DataSourceConnection connection,
            DataSourceTablePath table,
            YakTableSchema schema,
            int fetchSize,
            int readBatchSize,
            int timeoutSeconds) {
        this(connection, table, schema, fetchSize, readBatchSize, timeoutSeconds, null, null);
    }

    public JdbcSourceConfig(
            DataSourceConnection connection,
            DataSourceTablePath table,
            YakTableSchema schema,
            int fetchSize,
            int readBatchSize,
            int timeoutSeconds,
            JdbcNumericSplitConfig splitConfig) {
        this(connection, table, schema, fetchSize, readBatchSize, timeoutSeconds, splitConfig, null);
    }

    public JdbcSourceConfig(
            DataSourceConnection connection,
            DataSourceTablePath table,
            YakTableSchema schema,
            int fetchSize,
            int readBatchSize,
            int timeoutSeconds,
            Long splitSize) {
        this(connection, table, schema, fetchSize, readBatchSize, timeoutSeconds, null, splitSize);
    }

    public static JdbcSourceConfig defaults(
            DataSourceConnection connection, DataSourceTablePath table, YakTableSchema schema) {
        return new JdbcSourceConfig(
                connection, table, schema, DEFAULT_FETCH_SIZE, DEFAULT_READ_BATCH_SIZE, DEFAULT_TIMEOUT_SECONDS);
    }

    private static void validateSplitConfig(YakTableSchema schema, JdbcNumericSplitConfig splitConfig) {
        if (splitConfig == null) return;
        String eligibleColumn = JdbcNumericSplitConfig.eligibleColumn(schema)
                .orElseThrow(() -> new IllegalArgumentException("numeric split requires a single integer primary key"));
        if (!eligibleColumn.equals(splitConfig.column())) {
            throw new IllegalArgumentException("numeric split column must be the single integer primary key");
        }
    }
}
