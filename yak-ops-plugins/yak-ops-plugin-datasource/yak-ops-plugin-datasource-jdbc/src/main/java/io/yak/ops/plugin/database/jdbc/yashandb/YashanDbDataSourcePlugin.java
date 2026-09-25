package io.yak.ops.plugin.database.jdbc.yashandb;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import java.util.Locale;

/** YashanDB JDBC datasource plugin. */
public final class YashanDbDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public String type() {
        return "YASHAN_DB";
    }

    @Override
    protected String displayName() {
        return "YashanDB";
    }

    @Override
    protected java.util.Set<String> aliases() {
        return java.util.Set.of("YASHANDB", "YASDB");
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:yasdb://{host}:{port}/{database}";
    }

    @Override
    protected int defaultPort() {
        return 1688;
    }

    @Override
    protected String defaultDriverClassName() {
        return "com.yashandb.jdbc.Driver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:yasdb://" + host + ":" + port + "/" + database;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.trim().toLowerCase(Locale.ROOT).startsWith("jdbc:yasdb://");
    }
}
