package io.yak.ops.plugin.database.jdbc.mysql;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;

/**
 * MySQL JDBC Provider，拥有 MySQL 默认端口、Driver 和 JDBC URL 规则。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public final class MySqlDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public String type() {
        return "MYSQL";
    }

    @Override
    protected int defaultPort() {
        return 3306;
    }

    @Override
    protected String defaultDriverClassName() {
        return "com.mysql.cj.jdbc.Driver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:mysql:");
    }
}
