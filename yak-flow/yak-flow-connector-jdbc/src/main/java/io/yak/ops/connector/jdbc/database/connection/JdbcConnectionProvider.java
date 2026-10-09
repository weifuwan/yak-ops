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

    Connection getConnection() throws SQLException;
}
