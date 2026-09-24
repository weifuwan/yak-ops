package io.yak.ops.plugin.database.jdbc.mysql;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;

/** MySQL JDBC datasource plugin. */
public final class MySqlDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public DataSourceDbType dbType() {
        return DataSourceDbType.MYSQL;
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:mysql://{host}:{port}/{database}";
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
