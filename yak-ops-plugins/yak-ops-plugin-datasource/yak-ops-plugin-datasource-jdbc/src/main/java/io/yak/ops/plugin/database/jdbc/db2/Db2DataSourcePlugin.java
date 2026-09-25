package io.yak.ops.plugin.database.jdbc.db2;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;

/** IBM Db2 LUW JDBC datasource plugin. */
public final class Db2DataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public String type() {
        return "DB2";
    }

    @Override
    protected String displayName() {
        return "IBM Db2";
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:db2://{host}:{port}/{database}";
    }

    @Override
    protected int defaultPort() {
        return 50000;
    }

    @Override
    protected String defaultDriverClassName() {
        return "com.ibm.db2.jcc.DB2Driver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:db2://" + host + ":" + port + "/" + database;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase(java.util.Locale.ROOT).startsWith("jdbc:db2:");
    }
}
