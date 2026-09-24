package io.yak.ops.plugin.database.jdbc.gbase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import java.util.Locale;

/** GBase 8c datasource plugin aligned with Link-Up's PostgreSQL-style gbase8c dialect. */
public final class GBase8cDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public DataSourceDbType dbType() {
        return DataSourceDbType.GBASE8C;
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:gbase8c://{host}:{port}/{database}";
    }

    @Override
    protected int defaultPort() {
        return 5432;
    }

    @Override
    protected String defaultDriverClassName() {
        return "com.gbase8c.Driver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:gbase8c://" + host + ":" + port + "/" + database;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.trim().toLowerCase(Locale.ROOT).startsWith("jdbc:gbase8c://");
    }

    @Override
    protected void appendNormalizedFields(JsonNode source, ObjectNode normalized) {
        normalized.put("dialect", "gbase8c");
        if (!normalized.hasNonNull("schema")
                || normalized.path("schema").asText().isBlank()) {
            normalized.put("schema", "public");
        }
    }
}
