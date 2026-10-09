package io.yak.ops.connector.jdbc.database.dialect;

import java.util.Locale;

/** Database-type resolver shared by JDBC metadata planning and bounded Source readers. */
public final class JdbcDialects {

    private JdbcDialects() {}

    /** Selects exactly one database provider through Java SPI. */
    public static JdbcDialect forUrl(String jdbcUrl) {
        return io.yak.ops.connector.jdbc.database.JdbcFactoryLoader.loadDialect(jdbcUrl);
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
            case "MYSQL" -> forUrl("jdbc:mysql:");
            case "POSTGRE_SQL" -> forUrl("jdbc:postgresql:");
            case "ORACLE" -> forUrl("jdbc:oracle:");
            default -> throw new IllegalStateException("unreachable JDBC dialect type");
        };
    }
}
