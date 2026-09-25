package io.yak.ops.plugin.database.jdbc.postgresql;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;

/**
 * PostgreSQL JDBC Provider，拥有 PostgreSQL 默认端口、Driver、URL 和兼容类型别名。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public final class PostgreSqlDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public String type() {
        return "POSTGRE_SQL";
    }

    @Override
    protected java.util.Set<String> aliases() {
        return java.util.Set.of("POSTGRESQL", "POSTGRES");
    }

    @Override
    protected int defaultPort() {
        return 5432;
    }

    @Override
    protected String defaultDriverClassName() {
        return "org.postgresql.Driver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:postgresql:");
    }
}
