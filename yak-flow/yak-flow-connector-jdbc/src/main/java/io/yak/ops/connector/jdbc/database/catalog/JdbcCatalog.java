package io.yak.ops.connector.jdbc.database.catalog;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.List;

/**
 * Exposes read-only database, schema and table metadata for JDBC source/sink planning.
 *
 * <p>Database, schema and table names remain separate identifiers. Implementations open
 * independent connections per metadata operation and return YakFlow logical types, without
 * importing Flink Table API or product Datasource metadata DTOs.
 */
public interface JdbcCatalog extends AutoCloseable, Serializable {

    /**
     * Lists database catalogs visible to the configured connection.
     *
     * <p>PostgreSQL and some vendors expose only the current connection's database.
     *
     * @return visible catalog/database names
     * @throws SQLException if database discovery fails
     */
    List<String> listDatabases() throws SQLException;

    /**
     * Lists schemas visible in the requested catalog or current connection namespace.
     *
     * @param database optional database/catalog selector
     * @return schema names, subject to vendor namespace support
     * @throws SQLException if schema discovery fails
     */
    List<String> listSchemas(String database) throws SQLException;

    /**
     * Lists physical tables and views in an explicit or connection-default namespace.
     *
     * @param database optional database/catalog selector
     * @param schema optional schema/owner selector
     * @return discovered table IDs without concatenating identifier components
     * @throws SQLException if listing the namespace fails
     */
    List<TableId> listTables(String database, String schema) throws SQLException;

    /**
     * Resolves a table's ordered columns and declared primary-key sequence.
     *
     * @param tableId exact physical table identity
     * @return schema with normalized logical types and primary keys in JDBC KEY_SEQ order
     * @throws SQLException if metadata is missing or cannot be resolved safely
     */
    TableSchema getTable(TableId tableId) throws SQLException;

    /**
     * Checks a table's existence without swallowing connection or metadata errors.
     *
     * @param tableId exact table identity to find
     * @return true when the table is present
     * @throws SQLException when the lookup fails for reasons other than absence
     */
    boolean tableExists(TableId tableId) throws SQLException;

    /** Releases catalog resources; built-in implementations keep no persistent connection. */
    @Override
    void close();
}
