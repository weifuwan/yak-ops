package io.yak.ops.connector.jdbc.database.connection;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Task-independent capability to open a fresh, caller-owned JDBC connection.
 *
 * <p>Implementations may use DriverManager, an isolated JDBC driver runtime, or SSH tunnelling.
 * Each invocation must provide a distinct connection; SourceReader and Enumerator never share
 * the same mutable Connection instance.
 */
@FunctionalInterface
public interface JdbcConnectionProvider extends Serializable {

    /**
     * Opens a new JDBC connection for the caller to own and close.
     *
     * <p>Two invocations must not share mutable connection or transaction state; a Source
     * enumerator, reader, and Sink Writer each own their separate connections.
     *
     * @return a fresh, non-null JDBC connection
     * @throws SQLException if opening the connection fails
     */
    Connection getConnection() throws SQLException;
}
