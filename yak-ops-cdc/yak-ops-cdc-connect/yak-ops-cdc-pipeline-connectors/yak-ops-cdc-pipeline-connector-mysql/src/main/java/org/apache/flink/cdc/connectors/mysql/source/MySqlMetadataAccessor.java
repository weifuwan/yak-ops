package org.apache.flink.cdc.connectors.mysql.source;

import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.source.MetadataAccessor;
import org.apache.flink.cdc.connectors.mysql.source.config.MySqlSourceConfig;
import org.apache.flink.cdc.connectors.mysql.utils.MySqlSchemaUtils;

import io.debezium.connector.mysql.MySqlPartition;

import javax.annotation.Nullable;

import java.util.List;

/** {@link MetadataAccessor} for {@link MySqlDataSource}. */
@Internal
public class MySqlMetadataAccessor implements MetadataAccessor {

    private final MySqlSourceConfig sourceConfig;

    private final MySqlPartition partition;

    public MySqlMetadataAccessor(MySqlSourceConfig sourceConfig) {
        this.sourceConfig = sourceConfig;
        this.partition =
                new MySqlPartition(sourceConfig.getMySqlConnectorConfig().getLogicalName());
    }

    /**
     * Always throw {@link UnsupportedOperationException} because MySQL does not support namespace.
     */
    @Override
    public List<String> listNamespaces() {
        throw new UnsupportedOperationException("List namespace is not supported by MySQL.");
    }

    /**
     * List all database from MySQL.
     *
     * @param namespace This parameter is ignored because MySQL does not support namespace.
     * @return The list of database
     */
    @Override
    public List<String> listSchemas(@Nullable String namespace) {
        return MySqlSchemaUtils.listDatabases(sourceConfig);
    }

    /**
     * List tables from MySQL.
     *
     * @param namespace This parameter is ignored because MySQL does not support namespace.
     * @param dbName The database to list tables from. If null, list tables from all databases.
     * @return The list of {@link TableId}s.
     */
    @Override
    public List<TableId> listTables(@Nullable String namespace, @Nullable String dbName) {
        return MySqlSchemaUtils.listTables(sourceConfig, dbName);
    }

    /**
     * Get the {@link Schema} of the given table.
     *
     * @param tableId The {@link TableId} of the given table.
     * @return The {@link Schema} of the table.
     */
    @Override
    public Schema getTableSchema(TableId tableId) {
        return MySqlSchemaUtils.getTableSchema(sourceConfig, partition, tableId);
    }
}
