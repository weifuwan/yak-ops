package io.yak.ops.flow.connector.cdc.mysql.source;

import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.nio.file.Path;
import java.util.Objects;

/**
 * MySQL CDC Source 配置，连接信息直接复用 Datasource 已规范化的 JDBC 连接对象。
 *
 * @param connection MySQL 结构化连接
 * @param table 需要捕获的单表
 * @param schema YakFlow 逻辑表结构
 * @param stateDirectory Debezium offset 与 schema history 持久化目录
 * @param name 当前 CDC 引擎稳定名称
 * @param serverId MySQL replication server id，必须在实例范围内唯一
 * @param queueCapacity Debezium batch 到 Reader 的有界队列容量
 * @param pollBatchSize 每次 SourceReader.poll 最多输出的 YakRow 数
 * @param timeoutSeconds 网络/SSH 建连超时秒数
 * @author weifuwan
 * @since 2026-09-27
 */
public record MySqlCdcSourceConfig(
        JdbcConnectionProperties connection,
        DataSourceTablePath table,
        YakTableSchema schema,
        Path stateDirectory,
        String name,
        long serverId,
        int queueCapacity,
        int pollBatchSize,
        int timeoutSeconds) {

    private static final int DEFAULT_QUEUE_CAPACITY = 64;
    private static final int DEFAULT_POLL_BATCH_SIZE = 500;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    public MySqlCdcSourceConfig {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(table, "table must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(stateDirectory, "stateDirectory must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (!"MYSQL".equals(connection.type()))
            throw new IllegalArgumentException("MySQL CDC only accepts MYSQL connection");
        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (serverId < 1 || serverId > 4_294_967_295L) {
            throw new IllegalArgumentException("serverId must be between 1 and 4294967295");
        }
        if (queueCapacity <= 0) throw new IllegalArgumentException("queueCapacity must be greater than 0");
        if (pollBatchSize <= 0) throw new IllegalArgumentException("pollBatchSize must be greater than 0");
        if (timeoutSeconds <= 0) throw new IllegalArgumentException("timeoutSeconds must be greater than 0");
        if (schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("Phase 4 MySQL CDC requires primary key");
        }
        String database =
                table.database() != null && !table.database().isBlank() ? table.database() : connection.database();
        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("MySQL CDC requires database name");
        }
    }

    public static MySqlCdcSourceConfig defaults(
            JdbcConnectionProperties connection,
            DataSourceTablePath table,
            YakTableSchema schema,
            Path stateDirectory,
            String name,
            long serverId) {
        return new MySqlCdcSourceConfig(
                connection,
                table,
                schema,
                stateDirectory,
                name,
                serverId,
                DEFAULT_QUEUE_CAPACITY,
                DEFAULT_POLL_BATCH_SIZE,
                DEFAULT_TIMEOUT_SECONDS);
    }

    public String databaseName() {
        if (table.database() != null && !table.database().isBlank()) {
            return table.database();
        }
        return connection.database();
    }
}
