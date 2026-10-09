package io.yak.ops.flow.connector.jdbc.dialect;

import java.util.Locale;

/**
 * 根据 Datasource 稳定类型选择 YakFlow 当前支持的 JDBC SQL 方言。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public final class JdbcDialects {

    private static final JdbcDialect MYSQL = new MySqlJdbcDialect();
    private static final JdbcDialect POSTGRESQL = new PostgreSqlJdbcDialect();
    private static final JdbcDialect ORACLE = new OracleJdbcDialect();

    private JdbcDialects() {}

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
