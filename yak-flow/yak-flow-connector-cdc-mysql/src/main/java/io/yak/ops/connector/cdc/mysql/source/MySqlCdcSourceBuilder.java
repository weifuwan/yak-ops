package io.yak.ops.connector.cdc.mysql.source;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configures an unbounded MySQL Binlog source with explicit, frozen table schemas.
 *
 * <p>PR1 supports a fresh current-position start and recovery from a completed YakFlow
 * checkpoint. Historical initial offsets and snapshot/backfill belong to the hybrid phase.
 */
public final class MySqlCdcSourceBuilder {

    private String hostname;
    private int port = 3306;
    private String username;
    private String password;
    private int serverId;
    private String topicPrefix;
    private final Map<TableId, TableSchema> tables = new LinkedHashMap<>();
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
        if (tables.putIfAbsent(Objects.requireNonNull(table, "table"), Objects.requireNonNull(schema, "schema"))
                != null) {
            throw new IllegalArgumentException("Duplicate MySQL CDC table");
        }
        return this;
    }

    public MySqlCdcSourceBuilder queueCapacity(int value) {
        queueCapacity = value;
        return this;
    }

    /** Builds a reusable Source definition without opening a database connection. */
    public MySqlCdcSource build() {
        return new MySqlCdcSource(new MySqlCdcSourceConfig(
                hostname, port, username, password, serverId, topicPrefix, tables, queueCapacity));
    }
}
