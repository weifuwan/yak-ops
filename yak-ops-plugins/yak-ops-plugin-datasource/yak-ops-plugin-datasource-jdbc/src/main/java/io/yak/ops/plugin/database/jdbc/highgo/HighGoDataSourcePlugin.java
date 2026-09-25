package io.yak.ops.plugin.database.jdbc.highgo;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import java.util.Locale;

/** HighGo JDBC datasource plugin. */
public final class HighGoDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public String type() {
        return "HIGHGO";
    }

    @Override
    protected String displayName() {
        return "HighGo";
    }

    @Override
    protected java.util.Set<String> aliases() {
        return java.util.Set.of("HIGH_GO", "HGDB");
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:highgo://{host}:{port}/{database}";
    }

    @Override
    protected int defaultPort() {
        return 5866;
    }

    @Override
    protected String defaultDriverClassName() {
        return "com.highgo.jdbc.Driver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:highgo://" + host + ":" + port + "/" + database;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.trim().toLowerCase(Locale.ROOT).startsWith("jdbc:highgo://");
    }
}
