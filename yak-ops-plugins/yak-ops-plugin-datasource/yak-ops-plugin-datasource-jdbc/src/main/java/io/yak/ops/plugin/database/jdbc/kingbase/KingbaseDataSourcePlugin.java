package io.yak.ops.plugin.database.jdbc.kingbase;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;

/** KingbaseES JDBC datasource plugin. */
public final class KingbaseDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public DataSourceDbType dbType() {
        return DataSourceDbType.KINGBASE;
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:kingbase8://{host}:{port}/{database}";
    }

    @Override
    protected int defaultPort() {
        return 54321;
    }

    @Override
    protected String defaultDriverClassName() {
        return "com.kingbase8.Driver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:kingbase8://" + host + ":" + port + "/" + database;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:kingbase8:");
    }
}
