package io.yak.ops.connector.jdbc.database.dialect;

import java.util.Locale;

/**
 * Resolves configured database types to Connector-owned JDBC dialect implementations.
 *
 * <p>Type aliases normalize to the product's stable database identifiers. Dialect
 * instances are loaded through the same factory SPI as the Connector Catalog; unknown
 * vendors fail explicitly rather than silently falling back to generic SQL.
 */
public final class JdbcDialects {

    private JdbcDialects() {}

    /** Selects exactly one database provider through Java SPI. */
    public static JdbcDialect forUrl(String jdbcUrl) {
        return io.yak.ops.connector.jdbc.database.JdbcFactoryLoader.loadDialect(jdbcUrl);
    }

    /**
     * Normalizes supported database-name aliases into the stable type identifiers.
     *
     * @param type supported MySQL, PostgreSQL, or Oracle type name/alias
     * @return canonical database type used by Connector clients
     * @throws IllegalArgumentException if the type is blank or unsupported
     */
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

    /**
     * Resolves the vendor dialect for a recognized canonical type or alias.
     *
     * @param type source/target database type name
     * @return vendor dialect supplied through JDBC factory SPI
     * @throws IllegalArgumentException if the database type is unsupported
     */
    public static JdbcDialect forType(String type) {
        return switch (canonicalType(type)) {
            case "MYSQL" -> forUrl("jdbc:mysql:");
            case "POSTGRE_SQL" -> forUrl("jdbc:postgresql:");
            case "ORACLE" -> forUrl("jdbc:oracle:");
            default -> throw new IllegalStateException("unreachable JDBC dialect type");
        };
    }
}
