package io.yak.ops.connector.jdbc.database.dialect;

import io.yak.ops.core.data.TableId;
import java.util.Objects;

/**
 * Vendor-specific SQL identifier handling, kept inside the JDBC Connector.
 *
 * <p>Only table identifiers and column names may be interpolated into SQL. Split bounds are
 * always bound as prepared-statement parameters.
 */
public interface JdbcDialect {

    String quoteIdentifier(String identifier);

    String qualifiedTable(TableId tableId);

    /** Applies database-specific cursor settings to a newly opened read-only JDBC connection. */
    default void configureReadConnection(java.sql.Connection connection) throws java.sql.SQLException {
        connection.setReadOnly(true);
    }

    static String requireIdentifier(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("SQL identifier must not be blank");
        }
        return identifier;
    }
}
