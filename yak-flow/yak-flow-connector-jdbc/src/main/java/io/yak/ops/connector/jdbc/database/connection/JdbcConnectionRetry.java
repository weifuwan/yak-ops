package io.yak.ops.connector.jdbc.database.connection;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.util.Objects;

/**
 * Bounded retry for opening a new JDBC connection before any query or row consumption.
 *
 * <p>Never retries an in-flight ResultSet: prefetched records could already have been emitted,
 * and only Runtime recovery from a durable checkpoint can safely replay an interrupted split.
 */
public final class JdbcConnectionRetry {

    private JdbcConnectionRetry() {}

    public static Connection open(JdbcConnectionProvider provider, int maxAttempts) throws SQLException {
        Objects.requireNonNull(provider, "provider");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("JDBC connection attempts must be positive");
        }
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Connection connection = provider.getConnection();
                if (connection == null) {
                    throw new SQLException("JDBC provider returned no Connection");
                }
                return connection;
            } catch (SQLException exception) {
                if (attempt == maxAttempts || !isRetryable(exception) || Thread.currentThread().isInterrupted()) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("Connection retry loop ended without a result");
    }

    private static boolean isRetryable(SQLException failure) {
        if (failure instanceof SQLTransientConnectionException || failure instanceof SQLRecoverableException) {
            return true;
        }
        String state = failure.getSQLState();
        return state != null && state.startsWith("08");
    }
}
