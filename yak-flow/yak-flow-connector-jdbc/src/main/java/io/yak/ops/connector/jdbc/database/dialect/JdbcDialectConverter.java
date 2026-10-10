package io.yak.ops.connector.jdbc.database.dialect;

import io.yak.ops.core.data.RowData;
import io.yak.ops.core.types.TableSchema;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Converts JDBC driver values to canonical YakFlow {@link RowData} and binds them back to SQL.
 *
 * <p>The schema fixes the number, order, logical types, and nullability of fields. A source
 * converter derives this contract from ResultSet metadata; a sink converter receives a
 * resolved target schema. Unsupported driver values and unresolved types must fail instead
 * of being passed through as vendor-specific Java objects.
 */
public interface JdbcDialectConverter {

    /**
     * Returns the resolved field contract used by both conversion directions.
     *
     * @return the ordered schema with canonical logical field types
     */
    TableSchema schema();

    /**
     * Reads the current ResultSet row into a detached, canonical internal row.
     *
     * @param resultSet positioned at a valid current record
     * @return values in {@link #schema()} column order
     * @throws SQLException if a driver value violates the resolved type contract
     */
    RowData toInternal(ResultSet resultSet) throws SQLException;

    /**
     * Binds a row to JDBC parameter positions 1 through the schema's column count.
     *
     * <p>The caller owns the PreparedStatement and decides when to add or execute batches.
     * Nullability, decimal precision, and exact internal Java types are validated.
     *
     * @param row row with arity and value types matching {@link #schema()}
     * @param statement open statement with parameters in schema column order
     * @throws SQLException if a field is incompatible with the JDBC target
     */
    void toExternal(RowData row, PreparedStatement statement) throws SQLException;
}
