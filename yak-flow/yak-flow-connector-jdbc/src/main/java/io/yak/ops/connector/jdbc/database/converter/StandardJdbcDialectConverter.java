package io.yak.ops.connector.jdbc.database.converter;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/** ANSI/JDBC metadata to the standard YakFlow internal type representations. */
public final class StandardJdbcDialectConverter extends AbstractJdbcDialectConverter {

    public StandardJdbcDialectConverter(ResultSetMetaData metadata) throws SQLException {
        super(metadata);
    }
}
