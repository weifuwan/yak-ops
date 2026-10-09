package io.yak.ops.connector.jdbc.database.converter;

import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypes;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Locale;

/** PostgreSQL JSONB, JSON, UUID, and XML are normalized into STRING for bounded transfer. */
public final class PostgresJdbcDialectConverter extends AbstractJdbcDialectConverter {

    public PostgresJdbcDialectConverter(ResultSetMetaData metadata) throws SQLException {
        super(metadata);
    }

    @Override
    protected LogicalType resolveType(ResultSetMetaData metadata, int index) throws SQLException {
        if (metadata.getColumnType(index) == Types.OTHER) {
            String nativeType = metadata.getColumnTypeName(index).toLowerCase(Locale.ROOT);
            if (nativeType.equals("json") || nativeType.equals("jsonb") || nativeType.equals("uuid")) {
                return LogicalTypes.STRING;
            }
        }
        return super.resolveType(metadata, index);
    }
}
