package io.yak.ops.connector.jdbc.database.catalog;

import io.yak.ops.core.data.TableId;
import java.util.Objects;

/**
 * Physical JDBC table or view identity and display metadata.
 *
 * <p>Native JDBC TABLE_TYPE and REMARKS are preserved for Datasource clients while
 * the actual field types and ordered primary keys remain owned by Core TableSchema.
 *
 * @param tableId exact JDBC catalog, schema and table identity
 * @param type native TABLE or VIEW type
 * @param remarks optional table comment
 */
public record JdbcTableInfo(TableId tableId, String type, String remarks) {

    public JdbcTableInfo {
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(type, "type");
    }
}
