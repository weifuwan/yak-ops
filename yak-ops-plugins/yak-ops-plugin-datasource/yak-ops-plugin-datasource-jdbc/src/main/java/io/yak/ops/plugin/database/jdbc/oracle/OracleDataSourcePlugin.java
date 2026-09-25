package io.yak.ops.plugin.database.jdbc.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;

/** Oracle JDBC datasource plugin. */
public final class OracleDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public String type() {
        return "ORACLE";
    }

    @Override
    protected String displayName() {
        return "Oracle";
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:oracle:thin:@//{host}:{port}/{database}";
    }

    @Override
    protected int defaultPort() {
        return 1521;
    }

    @Override
    protected String defaultDriverClassName() {
        return "oracle.jdbc.OracleDriver";
    }

    @Override
    protected String databaseLabel() {
        return "服务名 / 数据库";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:oracle:thin:@//" + host + ":" + port + "/" + database;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:oracle:");
    }
}
