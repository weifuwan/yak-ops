package io.yak.ops.connector.jdbc.database.catalog;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.List;

/**
 * Connector-owned read-only relational Catalog for a JDBC connection.
 *
 * <p>Database/schema/table are separate identifiers. This contract intentionally does not
 * import Flink's Table API or the Yak Ops Datasource Plugin's metadata DTOs.
 */
public interface JdbcCatalog extends AutoCloseable, Serializable {

    /** Database names visible to this connection (may be limited to the current database). */
    List<String> listDatabases() throws SQLException;

    /** Schemas visible within the requested database. Some dialects have no schema namespace. */
    List<String> listSchemas(String database) throws SQLException;

    /** Lists tables and views in one explicit or connection-default namespace. */
    List<TableId> listTables(String database, String schema) throws SQLException;

    /** Resolves a table's ordered columns and primary keys with Core logical type semantics. */
    TableSchema getTable(TableId tableId) throws SQLException;

    /** Checks existence without treating a database failure as a missing table. */
    boolean tableExists(TableId tableId) throws SQLException;

    /** No persistent connection is retained by the built-in implementations. */
    @Override
    void close();
}
