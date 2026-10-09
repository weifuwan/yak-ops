package io.yak.ops.connector.jdbc.database.converter;

import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypes;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Oracle DATE retains its time-of-day and is emitted as LocalDateTime, unlike JDBC DATE.
 *
 * <p>Oracle NUMBER remains a checked decimal unless the source actually declares a JDBC
 * integer type; no lossy automatic conversion to Long or Boolean is performed.
 */
public final class OracleJdbcDialectConverter extends AbstractJdbcDialectConverter {

    public OracleJdbcDialectConverter(ResultSetMetaData metadata) throws SQLException {
        super(metadata);
    }

    @Override
    protected LogicalType resolveType(ResultSetMetaData metadata, int index) throws SQLException {
        if (metadata.getColumnType(index) == Types.DATE) {
            return LogicalTypes.timestamp(0);
        }
        return super.resolveType(metadata, index);
    }
}
