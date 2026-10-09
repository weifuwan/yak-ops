package io.yak.ops.connector.jdbc.database.connection;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** DriverManager-backed provider used when no external connection runtime is supplied. */
public final class DriverManagerJdbcConnectionProvider implements JdbcConnectionProvider {

    private final JdbcConnectionOptions options;

    public DriverManagerJdbcConnectionProvider(JdbcConnectionOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public Connection getConnection() throws SQLException {
        return options.openConnection();
    }
}
