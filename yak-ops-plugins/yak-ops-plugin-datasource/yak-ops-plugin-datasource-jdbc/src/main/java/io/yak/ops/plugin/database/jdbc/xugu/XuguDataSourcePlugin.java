package io.yak.ops.plugin.database.jdbc.xugu;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import java.util.Locale;

/** XuguDB JDBC datasource plugin. */
public final class XuguDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public String type() {
        return "XUGU";
    }

    @Override
    protected String displayName() {
        return "XuguDB";
    }

    @Override
    protected java.util.Set<String> aliases() {
        return java.util.Set.of("XUGUDB");
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:xugu://{host}:{port}/{database}";
    }

    @Override
    protected int defaultPort() {
        return 5138;
    }

    @Override
    protected String defaultDriverClassName() {
        return "com.xugu.cloudjdbc.Driver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:xugu://" + host + ":" + port + "/" + database;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.trim().toLowerCase(Locale.ROOT).startsWith("jdbc:xugu://");
    }
}
