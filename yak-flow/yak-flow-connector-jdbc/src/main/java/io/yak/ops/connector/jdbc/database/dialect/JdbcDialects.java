package io.yak.ops.connector.jdbc.database.dialect;

/** Resolves built-in JDBC read dialects without exposing vendor details to Core or Runtime. */
public final class JdbcDialects {

    private JdbcDialects() {}

    public static JdbcDialect forUrl(String jdbcUrl) {
        if (jdbcUrl.startsWith("jdbc:mysql:")) {
            return new MySqlJdbcDialect();
        }
        if (jdbcUrl.startsWith("jdbc:postgresql:")) {
            return new PostgresJdbcDialect();
        }
        if (jdbcUrl.startsWith("jdbc:oracle:")) {
            return new OracleJdbcDialect();
        }
        if (jdbcUrl.startsWith("jdbc:h2:")) {
            return new AnsiJdbcDialect();
        }
        throw new IllegalArgumentException("Unsupported JDBC database type");
    }
}
