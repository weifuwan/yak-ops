package io.yak.ops.flow.connector.jdbc;

import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import java.util.Objects;

/**
 * JDBC Sink 的批量写入配置；目标表必须在执行前存在。
 *
 * @param connection 已规范化的数据源连接
 * @param table 目标表路径
 * @param batchSize 每次提交前最多累计的行数
 * @param timeoutSeconds 连接与语句超时秒数
 * @param saveMode 写入前对目标已有数据的处理方式
 * @param writeMode 行级写入语义：离线 INSERT / UPSERT 或 CDC CHANGELOG
 * @author weifuwan
 * @since 2026-09-27
 */
public record JdbcSinkConfig(
        DataSourceConnection connection,
        DataSourceTablePath table,
        int batchSize,
        int timeoutSeconds,
        JdbcSaveMode saveMode,
        JdbcWriteMode writeMode) {

    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    public JdbcSinkConfig(
            DataSourceConnection connection, DataSourceTablePath table, int batchSize, int timeoutSeconds) {
        this(connection, table, batchSize, timeoutSeconds, JdbcSaveMode.APPEND, JdbcWriteMode.INSERT);
    }

    public JdbcSinkConfig(
            DataSourceConnection connection,
            DataSourceTablePath table,
            int batchSize,
            int timeoutSeconds,
            JdbcWriteMode writeMode) {
        this(connection, table, batchSize, timeoutSeconds, JdbcSaveMode.APPEND, writeMode);
    }

    public JdbcSinkConfig(
            DataSourceConnection connection,
            DataSourceTablePath table,
            int batchSize,
            int timeoutSeconds,
            JdbcSaveMode saveMode) {
        this(connection, table, batchSize, timeoutSeconds, saveMode, JdbcWriteMode.INSERT);
    }

    public JdbcSinkConfig {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(table, "table must not be null");
        Objects.requireNonNull(saveMode, "saveMode must not be null");
        Objects.requireNonNull(writeMode, "writeMode must not be null");
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be greater than 0");
        if (timeoutSeconds <= 0) throw new IllegalArgumentException("timeoutSeconds must be greater than 0");
        if (saveMode == JdbcSaveMode.OVERWRITE && writeMode != JdbcWriteMode.INSERT) {
            throw new IllegalArgumentException("JDBC OVERWRITE save mode only supports INSERT write mode");
        }
    }

    public static JdbcSinkConfig defaults(DataSourceConnection connection, DataSourceTablePath table) {
        return new JdbcSinkConfig(
                connection,
                table,
                DEFAULT_BATCH_SIZE,
                DEFAULT_TIMEOUT_SECONDS,
                JdbcSaveMode.APPEND,
                JdbcWriteMode.INSERT);
    }

    public static JdbcSinkConfig changelogDefaults(DataSourceConnection connection, DataSourceTablePath table) {
        return new JdbcSinkConfig(
                connection,
                table,
                DEFAULT_BATCH_SIZE,
                DEFAULT_TIMEOUT_SECONDS,
                JdbcSaveMode.APPEND,
                JdbcWriteMode.CHANGELOG);
    }
}
