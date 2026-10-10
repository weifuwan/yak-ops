package io.yak.ops.connector.jdbc.database.dialect;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;

/**
 * Database-specific identifier, type conversion and SQL planning protocol.
 *
 * <p>Concrete vendor dialects live in internal/dialect; callers depend only on this
 * interface, like Flink's core JdbcDialect contract.
 */
public interface JdbcDialect extends java.io.Serializable {

    String quoteIdentifier(String identifier);

    String qualifiedTable(TableId table);

    JdbcDialectConverter createRowConverter(ResultSetMetaData metadata) throws SQLException;

    /** Builds a JDBC write binder from an already resolved target schema. */
    JdbcDialectConverter createRowConverter(TableSchema schema);

    void configureReadConnection(Connection connection) throws SQLException;

    JdbcNativeType nativeType(Column column);

    String createTableSql(TableId table, TableSchema schema);

    JdbcDdlPlan createTablePlan(
            TableId table, TableSchema schema, String tableComment, Map<String, String> columnComments);

    String stringLiteral(String value);

    String selectSql(TableId table, TableSchema schema);

    String selectRangeSql(TableId table, TableSchema schema, String splitColumn);

    String splitStatisticsSql(TableId table, String splitColumn);

    String truncateSql(TableId table);

    String upsertSql(TableId table, TableSchema schema);

    String insertSql(TableId table, TableSchema schema);

    String deleteSql(TableId table, TableSchema schema);

    static String requireIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("SQL identifier must not be blank");
        }
        return identifier;
    }
}
