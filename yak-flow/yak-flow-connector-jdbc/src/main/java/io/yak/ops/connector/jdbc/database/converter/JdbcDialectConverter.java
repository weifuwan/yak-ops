package io.yak.ops.connector.jdbc.database.converter;

import io.yak.ops.core.data.RowData;
import io.yak.ops.core.types.TableSchema;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Converts JDBC driver values to canonical YakFlow RowData and binds canonical values to JDBC.
 *
 * <p>One converter is created per active ResultSet from its JDBC metadata. It must reject
 * unresolved and unsupported column types rather than return arbitrary driver objects.
 */
public interface JdbcDialectConverter {

    TableSchema schema();

    RowData toInternal(ResultSet resultSet) throws SQLException;

    void toExternal(RowData row, PreparedStatement statement) throws SQLException;
}
