package io.yak.ops.connector.jdbc.database.dialect;

import java.util.Locale;

/** Database-type resolver shared by JDBC metadata planning and bounded Source readers. */
public final class JdbcDialects {

    private static final JdbcDialect MYSQL = new MySqlJdbcDialect();
    private static final JdbcDialect POSTGRESQL = new PostgresJdbcDialect();
    private static final JdbcDialect ORACLE = new OracleJdbcDialect();

    private JdbcDialects() {}

    /** Resolves the SQL dialect from the JDBC connection URL used by SourceReader. */
    public static JdbcDialect forUrl(String jdbcUrl) {
        if (jdbcUrl == null) throw new IllegalArgumentException("JDBC URL must not be null");
        if (jdbcUrl.startsWith("jdbc:mysql:")) return MYSQL;
        if (jdbcUrl.startsWith("jdbc:postgresql:")) return POSTGRESQL;
        if (jdbcUrl.startsWith("jdbc:oracle:")) return ORACLE;
        if (jdbcUrl.startsWith("jdbc:h2:")) return new AnsiJdbcDialect();
        throw new IllegalArgumentException("Unsupported JDBC database type");
    }

    public static String canonicalType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("JDBC 数据源类型不能为空");
        }
        return switch (type.trim().toUpperCase(Locale.ROOT)) {
            case "MYSQL" -> "MYSQL";
            case "POSTGRE_SQL", "POSTGRESQL", "POSTGRES" -> "POSTGRE_SQL";
            case "ORACLE" -> "ORACLE";
            default -> throw new IllegalArgumentException("YakFlow JDBC 暂不支持数据源类型：" + type);
        };
    }

    public static JdbcDialect forType(String type) {
        return switch (canonicalType(type)) {
            case "MYSQL" -> MYSQL;
            case "POSTGRE_SQL" -> POSTGRESQL;
            case "ORACLE" -> ORACLE;
            default -> throw new IllegalStateException("unreachable JDBC dialect type");
        };
    }
}
