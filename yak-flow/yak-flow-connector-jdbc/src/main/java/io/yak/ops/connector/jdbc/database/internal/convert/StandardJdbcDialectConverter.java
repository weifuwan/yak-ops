package io.yak.ops.connector.jdbc.database.internal.convert;

import io.yak.ops.connector.jdbc.database.dialect.AbstractDialectConverter;
import io.yak.ops.core.types.TableSchema;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/** ANSI/JDBC metadata to the standard YakFlow internal type representations. */
public final class StandardJdbcDialectConverter extends AbstractDialectConverter {

    public StandardJdbcDialectConverter(ResultSetMetaData metadata) throws SQLException {
        super(metadata);
    }

    public StandardJdbcDialectConverter(TableSchema schema) {
        super(schema);
    }
}
