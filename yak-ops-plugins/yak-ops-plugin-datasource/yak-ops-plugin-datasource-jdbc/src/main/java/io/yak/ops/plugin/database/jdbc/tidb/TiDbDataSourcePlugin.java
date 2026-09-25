package io.yak.ops.plugin.database.jdbc.tidb;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.spi.datasource.DataSourceCatalog;
import java.util.Locale;

/** TiDB datasource plugin backed by the MySQL wire protocol and Connector/J. */
public final class TiDbDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public String type() {
        return "TIDB";
    }

    @Override
    protected String displayName() {
        return "TiDB";
    }

    @Override
    protected java.util.Set<String> aliases() {
        return java.util.Set.of("TI_DB");
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:mysql://{host}:{port}/{database}";
    }

    @Override
    protected int defaultPort() {
        return 4000;
    }

    @Override
    protected String defaultDriverClassName() {
        return "com.mysql.cj.jdbc.Driver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.trim().toLowerCase(Locale.ROOT).startsWith("jdbc:mysql:");
    }

    /**
     * TiDB exposes MySQL-compatible catalogs, schemas, identifier quoting and preview SQL. Keep the
     * datasource identity as TIDB while reusing the mature MySQL metadata path internally.
     */
    @Override
    protected DataSourceCatalog createJdbcCatalog(
            JdbcConnectionProperties connection, int connectionTimeoutSeconds, int queryTimeoutSeconds) {
        JdbcConnectionProperties mysqlCompatible = new JdbcConnectionProperties(
                "MYSQL",
                connection.host(),
                connection.port(),
                connection.jdbcUrl(),
                connection.driverClassName(),
                connection.username(),
                connection.password(),
                connection.database(),
                connection.schema(),
                connection.properties(),
                connection.sshTunnel(),
                connection.normalizedJson());
        return super.createJdbcCatalog(mysqlCompatible, connectionTimeoutSeconds, queryTimeoutSeconds);
    }
}
