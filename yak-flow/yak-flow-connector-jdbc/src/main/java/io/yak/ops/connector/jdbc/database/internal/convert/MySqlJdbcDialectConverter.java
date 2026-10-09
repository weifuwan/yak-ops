package io.yak.ops.connector.jdbc.database.internal.convert;

import io.yak.ops.connector.jdbc.database.dialect.AbstractDialectConverter;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypes;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

/** MySQL-specific BIT and unsigned numeric metadata normalization. */
public final class MySqlJdbcDialectConverter extends AbstractDialectConverter {

    public MySqlJdbcDialectConverter(ResultSetMetaData metadata) throws SQLException {
        super(metadata);
    }

    @Override
    protected LogicalType resolveType(ResultSetMetaData metadata, int index) throws SQLException {
        String name = metadata.getColumnTypeName(index).toUpperCase(java.util.Locale.ROOT);
        int type = metadata.getColumnType(index);
        if (type == Types.TINYINT && name.contains("UNSIGNED")) {
            return LogicalTypes.SMALLINT;
        }
        if (type == Types.SMALLINT && name.contains("UNSIGNED")) {
            return LogicalTypes.INTEGER;
        }
        if (type == Types.INTEGER && name.contains("UNSIGNED")) {
            return LogicalTypes.BIGINT;
        }
        if (type == Types.BIGINT && name.contains("UNSIGNED")) {
            return decimalType(20, 0, metadata.getColumnLabel(index));
        }
        return super.resolveType(metadata, index);
    }
}
