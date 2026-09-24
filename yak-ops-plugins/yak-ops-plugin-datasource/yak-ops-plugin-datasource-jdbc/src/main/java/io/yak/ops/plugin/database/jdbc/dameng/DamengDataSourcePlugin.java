package io.yak.ops.plugin.database.jdbc.dameng;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;

/** Dameng JDBC datasource plugin. */
public final class DamengDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public DataSourceDbType dbType() {
        return DataSourceDbType.DAMENG;
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:dm://{host}:{port}/{database}";
    }

    @Override
    protected int defaultPort() {
        return 5236;
    }

    @Override
    protected String defaultDriverClassName() {
        return "dm.jdbc.driver.DmDriver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:dm://" + host + ":" + port + "/" + database;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:dm:");
    }
}
