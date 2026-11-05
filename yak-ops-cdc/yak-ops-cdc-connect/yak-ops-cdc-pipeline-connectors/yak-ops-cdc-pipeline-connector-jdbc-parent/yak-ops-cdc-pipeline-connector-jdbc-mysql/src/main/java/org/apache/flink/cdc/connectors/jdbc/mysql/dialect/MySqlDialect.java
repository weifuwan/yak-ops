package org.apache.flink.cdc.connectors.jdbc.mysql.dialect;

import org.apache.flink.cdc.common.event.AddColumnEvent;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.connectors.jdbc.mysql.converter.MySqlRowConverter;
import org.apache.flink.cdc.connectors.jdbc.internal.converter.JdbcRowConverter;
import org.apache.flink.cdc.connectors.jdbc.internal.dialect.JdbcDialect;

import java.util.List;

/** MySQL SQL dialect; no connection or DDL execution is owned by this class. */
public class MySqlDialect implements JdbcDialect {

    @Override
    public String dialectName() {
        return "mysql";
    }

    @Override
    public JdbcRowConverter createRowConverter() {
        return new MySqlRowConverter();
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    @Override
    public String getUpsertStatement(TableId tableId, Schema schema) {
        return MySqlStmtCreatorFactory.INSTANCE.buildUpsertSql(tableId, schema.getColumns());
    }

    @Override
    public String getDeleteStatement(TableId tableId, Schema schema) {
        return MySqlStmtCreatorFactory.INSTANCE.buildDeleteSql(
                tableId,
                schema.primaryKeys().isEmpty() ? schema.getColumnNames() : schema.primaryKeys());
    }

    @Override
    public String buildCreateTableSql(TableId tableId, Schema schema, boolean ignoreIfExists) {
        return MySqlStmtCreatorFactory.INSTANCE.buildCreateTableSql(
                tableId, schema, ignoreIfExists);
    }

    @Override
    public String buildAlterAddColumnsSql(
            TableId tableId, List<AddColumnEvent.ColumnWithPosition> addedColumns) {
        return MySqlStmtCreatorFactory.INSTANCE.buildAlterAddColumnsSql(tableId, addedColumns);
    }

    @Override
    public String buildRenameColumnSql(TableId tableId, String oldName, String newName) {
        return MySqlStmtCreatorFactory.INSTANCE.buildRenameColumnSql(tableId, oldName, newName);
    }

    @Override
    public String buildDropColumnSql(TableId tableId, String columnName) {
        return MySqlStmtCreatorFactory.INSTANCE.buildDropColumnSql(tableId, columnName);
    }

    @Override
    public String buildAlterColumnTypeSql(TableId tableId, String name, DataType type) {
        return MySqlStmtCreatorFactory.INSTANCE.buildAlterColumnTypeSql(tableId, name, type);
    }

    @Override
    public String buildTruncateTableSql(TableId tableId) {
        return MySqlStmtCreatorFactory.INSTANCE.buildTruncateTableSql(tableId);
    }

    @Override
    public String buildDropTableSql(TableId tableId, boolean ignoreIfNotExists) {
        return MySqlStmtCreatorFactory.INSTANCE.buildDropTableSql(tableId, ignoreIfNotExists);
    }
}
