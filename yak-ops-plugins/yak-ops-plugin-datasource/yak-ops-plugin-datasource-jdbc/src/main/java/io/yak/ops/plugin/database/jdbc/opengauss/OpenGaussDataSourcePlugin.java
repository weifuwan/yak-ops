package io.yak.ops.plugin.database.jdbc.opengauss;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;

/** openGauss JDBC datasource plugin. */
public final class OpenGaussDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public String type() {
        return "OPEN_GAUSS";
    }

    @Override
    protected String displayName() {
        return "openGauss";
    }

    @Override
    protected java.util.Set<String> aliases() {
        return java.util.Set.of("OPENGAUSS");
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:opengauss://{host}:{port}/{database}";
    }

    @Override
    protected int defaultPort() {
        return 5432;
    }

    @Override
    protected String defaultDriverClassName() {
        return "org.opengauss.Driver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:opengauss://" + host + ":" + port + "/" + database;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase(java.util.Locale.ROOT).startsWith("jdbc:opengauss:");
    }
}
