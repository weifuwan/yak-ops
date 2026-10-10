package io.yak.ops.connector.jdbc.database.dialect;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;

/**
 * Represents the SQL dialect and JDBC value-conversion capabilities of one database vendor.
 *
 * <p>Implementations are stateless, reusable definitions. A dialect produces identifier-safe
 * SQL, native target types, and converters for resolved {@link TableSchema}s or JDBC result
 * metadata; it does not own connections, transaction commits, or source/sink task state.
 *
 * <p>Generated DML uses JDBC positional parameters. INSERT and UPSERT bind values in the
 * target schema's column order; DELETE binds primary keys in the order declared by the schema.
 * The JDBC Connector's concrete vendor implementations live under database/internal/dialect.
 */
public interface JdbcDialect extends java.io.Serializable {

    /**
     * Quotes one database identifier, escaping embedded quote characters for this vendor.
     *
     * <p>Callers pass one table, schema or column name, not an entire SQL expression or
     * dotted qualified name.
     *
     * @param identifier a nonblank unqualified identifier
     * @return the vendor-quoted identifier
     */
    String quoteIdentifier(String identifier);

    /**
     * Qualifies a table using the database's supported catalog and schema namespace.
     *
     * <p>Absent namespaces use the current connection's default. Each emitted name is
     * quoted separately rather than parsing a concatenated identifier.
     *
     * @param table physical table identity
     * @return the qualified, quoted SQL table reference
     */
    String qualifiedTable(TableId table);

    /**
     * Resolves JDBC result columns into canonical YakFlow row types and a matching row reader.
     *
     * @param metadata JDBC result column labels, types, precision, and nullability
     * @return a converter whose field order matches the result-set projection
     * @throws SQLException if metadata cannot be resolved safely
     */
    JdbcDialectConverter createRowConverter(ResultSetMetaData metadata) throws SQLException;

    /**
     * Creates a writer-side binder for an already resolved target schema.
     *
     * <p>Binding positions follow {@link TableSchema#columns()} exactly. Unlike the result-set
     * overload, this operation does not inspect a connection or infer missing column types.
     *
     * @param schema ordered target columns with resolved logical types
     * @return a positional JDBC parameter binder for that schema
     */
    JdbcDialectConverter createRowConverter(TableSchema schema);

    /**
     * Applies read-only connection settings required by this JDBC dialect.
     *
     * <p>Some vendors require a transaction for cursor fetching. The caller still owns the
     * connection and closes it after the bounded split is consumed.
     *
     * @param connection the source reader's open, caller-owned JDBC connection
     * @throws SQLException if the database rejects the required settings
     */
    void configureReadConnection(Connection connection) throws SQLException;

    /**
     * Maps a resolved YakFlow column to a native target DDL type.
     *
     * @param column target column, including precision, scale, length, and nullability
     * @return native DDL type and any capacity or primary-key restrictions
     * @throws UnsupportedOperationException if the vendor cannot represent the logical type
     */
    JdbcNativeType nativeType(Column column);

    /**
     * Builds a CREATE TABLE statement, preserving the specified column and primary-key order.
     *
     * @param table target table identifier
     * @param schema target columns and primary-key definition
     * @return one vendor-specific CREATE TABLE statement
     */
    String createTableSql(TableId table, TableSchema schema);

    /**
     * Plans table creation and supported table/column comments in execution order.
     *
     * <p>Some vendors place comments inside CREATE TABLE; others require subsequent DDL.
     * This only creates SQL text and never executes DDL.
     *
     * @param table target table identifier
     * @param schema ordered target schema
     * @param tableComment optional table comment
     * @param columnComments optional comments keyed by target column name
     * @return the CREATE TABLE SQL and its complete ordered DDL plan
     */
    JdbcDdlPlan createTablePlan(
            TableId table, TableSchema schema, String tableComment, Map<String, String> columnComments);

    /**
     * Escapes a string literal for generated DDL comment statements.
     *
     * <p>Data rows must use prepared-statement parameters rather than this literal renderer.
     *
     * @param value non-null SQL string literal value
     * @return the quoted, escaped SQL string literal
     */
    String stringLiteral(String value);

    /**
     * Selects the schema's columns in exactly the order expected by the source converter.
     *
     * @param table table to read
     * @param schema resolved read projection
     * @return a bounded-source SELECT statement without split-range placeholders
     */
    String selectSql(TableId table, TableSchema schema);

    /**
     * Adds inclusive lower and upper key bounds to the projected SELECT statement.
     *
     * <p>The first two JDBC parameters are the split column's inclusive lower and upper
     * bounds. Numeric split planning and cursor recovery belong to the JDBC Source.
     *
     * @param table source table identity
     * @param schema projected columns in result-set order
     * @param splitColumn the nonblank numeric split-key column
     * @return a range SELECT with two positional placeholders
     */
    String selectRangeSql(TableId table, TableSchema schema, String splitColumn);

    /**
     * Computes the minimum key, maximum key, and row count for split planning.
     *
     * @param table source table identity
     * @param splitColumn the nonblank numeric split-key column
     * @return a statistics query with MIN, MAX and COUNT in that order
     */
    String splitStatisticsSql(TableId table, String splitColumn);

    /**
     * Generates a potentially destructive target-table TRUNCATE statement.
     *
     * <p>The SQL must not be run implicitly when restoring a task or replaying a checkpoint.
     *
     * @param table target table identifier
     * @return vendor-specific TRUNCATE SQL
     */
    String truncateSql(TableId table);

    /**
     * Builds a native UPSERT statement using the target schema's positional column order.
     *
     * <p>The target must have a declared primary key. No SELECT-then-UPDATE fallback is
     * provided; unsupported vendors fail explicitly instead of emulating the write.
     *
     * @param table target table identifier
     * @param schema target columns and declared primary keys
     * @return a vendor-native, parameterized UPSERT statement
     * @throws UnsupportedOperationException if native UPSERT is not supported
     */
    String upsertSql(TableId table, TableSchema schema);

    /**
     * Builds a parameterized INSERT statement using the target schema's column order.
     *
     * <p>Each column contributes exactly one JDBC placeholder. The row converter binds
     * values in the same order.
     *
     * @param table target table identifier
     * @param schema ordered target columns
     * @return INSERT SQL with one placeholder per column
     */
    String insertSql(TableId table, TableSchema schema);

    /**
     * Builds a primary-key DELETE statement for the target table.
     *
     * <p>JDBC parameters follow {@link TableSchema#primaryKeys()} order, not the target
     * table's overall column order.
     *
     * @param table target table identifier
     * @param schema target schema containing at least one primary key
     * @return a DELETE statement with one equality parameter per key field
     * @throws IllegalArgumentException if the schema has no primary key
     */
    String deleteSql(TableId table, TableSchema schema);

    /**
     * Rejects empty identifier parts before quoting or building SQL.
     *
     * @param identifier the unqualified identifier
     * @return the unchanged nonblank identifier
     * @throws IllegalArgumentException if the identifier is null or blank
     */
    static String requireIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("SQL identifier must not be blank");
        }
        return identifier;
    }
}
