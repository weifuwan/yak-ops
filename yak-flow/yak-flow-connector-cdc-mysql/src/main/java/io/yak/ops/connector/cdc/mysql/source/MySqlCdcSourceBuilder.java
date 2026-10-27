package io.yak.ops.connector.cdc.mysql.source;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Defines MySQL CDC connection and table settings shared by both startup modes.
 *
 * <p>{@link MySqlSource} selects the runtime path. The typed build methods remain in
 * use by connector tests until the split and checkpoint contracts are unified.
 */
public final class MySqlCdcSourceBuilder {

    private String hostname;
    private int port = 3306;
    private String username;
    private String password;
    private Map<String, String> connectionProperties = Map.of();
    private int serverId;
    private String topicPrefix;
    private final Map<TableId, TableSchema> tables = new LinkedHashMap<>();
    private final Map<TableId, String> chunkKeyColumns = new LinkedHashMap<>();
    private int queueCapacity = 256;

    public MySqlCdcSourceBuilder hostname(String value) {
        hostname = value;
        return this;
    }

    public MySqlCdcSourceBuilder port(int value) {
        port = value;
        return this;
    }

    public MySqlCdcSourceBuilder username(String value) {
        username = value;
        return this;
    }

    public MySqlCdcSourceBuilder password(String value) {
        password = value;
        return this;
    }

    /** Shares MySQL JDBC settings across Snapshot and Debezium connections. */
    public MySqlCdcSourceBuilder connectionProperties(Map<String, String> value) {
        connectionProperties = Map.copyOf(Objects.requireNonNull(value, "connectionProperties"));
        return this;
    }

    /** Selects a unique MySQL replication client ID; it must not collide with other readers. */
    public MySqlCdcSourceBuilder serverId(int value) {
        serverId = value;
        return this;
    }

    /** Defines stable Debezium topic and offset namespace for checkpoint recovery. */
    public MySqlCdcSourceBuilder topicPrefix(String value) {
        topicPrefix = value;
        return this;
    }

    /** Adds a table and its exact, ordered source logical schema. */
    public MySqlCdcSourceBuilder table(TableId table, TableSchema schema) {
        return table(table, schema, null);
    }

    /** Configures the snapshot chunk column for this table independently of its physical primary key. */
    public MySqlCdcSourceBuilder table(TableId table, TableSchema schema, String chunkKeyColumn) {
        if (tables.putIfAbsent(Objects.requireNonNull(table, "table"), Objects.requireNonNull(schema, "schema"))
                != null) {
            throw new IllegalArgumentException("Duplicate MySQL CDC table");
        }
        if (chunkKeyColumn != null) {
            chunkKeyColumns.put(table, chunkKeyColumn);
        }
        return this;
    }

    public MySqlCdcSourceBuilder queueCapacity(int value) {
        queueCapacity = value;
        return this;
    }

    /** Creates the initial Snapshot + Binlog Source from the same validated connection plan. */
    public MySqlHybridCdcSource buildHybrid(int chunkSize) {
        return build().initialSnapshot(chunkSize);
    }

    /** Builds the shared snapshot and Binlog source with generation-scoped measurements. */
    public MySqlHybridCdcSource buildHybrid(int chunkSize, io.yak.ops.connector.base.metrics.TableMetrics metrics) {
        MySqlCdcSource source = build();
        return source.initialSnapshot(chunkSize, metrics);
    }

    /** Builds a reusable Source definition without opening a database connection. */
    public MySqlCdcSource build() {
        return build(new TableMetrics());
    }

    /** Shares pipeline measurements with a standalone Binlog source. */
    public MySqlCdcSource build(TableMetrics metrics) {
        return new MySqlCdcSource(
                new MySqlCdcSourceConfig(
                        hostname,
                        port,
                        username,
                        password,
                        serverId,
                        topicPrefix,
                        tables,
                        queueCapacity,
                        connectionProperties,
                        chunkKeyColumns),
                metrics);
    }
}
