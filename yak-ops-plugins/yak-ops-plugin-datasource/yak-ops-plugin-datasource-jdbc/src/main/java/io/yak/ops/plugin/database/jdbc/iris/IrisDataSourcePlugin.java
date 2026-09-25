package io.yak.ops.plugin.database.jdbc.iris;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import java.util.Locale;

/** InterSystems IRIS JDBC datasource plugin. */
public final class IrisDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public String type() {
        return "IRIS";
    }

    @Override
    protected String displayName() {
        return "InterSystems IRIS";
    }

    @Override
    protected java.util.Set<String> aliases() {
        return java.util.Set.of("INTERSYSTEMS_IRIS");
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:IRIS://{host}:{port}/{database}";
    }

    @Override
    protected int defaultPort() {
        return 1972;
    }

    @Override
    protected String defaultDriverClassName() {
        return "com.intersystems.jdbc.IRISDriver";
    }

    @Override
    protected String databaseLabel() {
        return "Namespace";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:IRIS://" + host + ":" + port + "/" + database;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.trim().toLowerCase(Locale.ROOT).startsWith("jdbc:iris://");
    }
}
