package io.yak.ops.plugin.database.jdbc.gbase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import java.util.Locale;

/** GBase 8a datasource plugin aligned with Link-Up's dedicated gbase8a JDBC dialect. */
public final class GBase8aDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public DataSourceDbType dbType() {
        return DataSourceDbType.GBASE8A;
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:gbase://{host}:{port}/{database}";
    }

    @Override
    protected int defaultPort() {
        return 5258;
    }

    @Override
    protected String defaultDriverClassName() {
        return "com.gbase.jdbc.Driver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:gbase://" + host + ":" + port + "/" + database;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.trim().toLowerCase(Locale.ROOT).startsWith("jdbc:gbase://");
    }

    @Override
    protected void appendNormalizedFields(JsonNode source, ObjectNode normalized) {
        normalized.put("dialect", "gbase8a");
    }
}
