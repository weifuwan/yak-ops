package org.apache.flink.cdc.connectors.jdbc.internal.dialect;

import org.apache.flink.cdc.common.event.AddColumnEvent;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.connectors.jdbc.internal.converter.JdbcRowConverter;

import java.io.Serializable;
import java.util.List;

/**
 * Stateless SQL dialect for a JDBC destination.
 *
 * <p>Implementations create SQL but never open a connection or execute a statement.
 */
public interface JdbcDialect extends Serializable {
    String dialectName();

    /** Creates the destination-specific JDBC row converter. */
    JdbcRowConverter createRowConverter();

    String quoteIdentifier(String identifier);

    String getUpsertStatement(TableId tableId, Schema schema);

    String getDeleteStatement(TableId tableId, Schema schema);

    String buildCreateTableSql(TableId tableId, Schema schema, boolean ignoreIfExists);

    String buildAlterAddColumnsSql(
            TableId tableId, List<AddColumnEvent.ColumnWithPosition> columns);

    String buildRenameColumnSql(TableId tableId, String oldName, String newName);

    String buildDropColumnSql(TableId tableId, String columnName);

    String buildAlterColumnTypeSql(TableId tableId, String name, DataType newType);

    String buildTruncateTableSql(TableId tableId);

    String buildDropTableSql(TableId tableId, boolean ignoreIfNotExists);
}
