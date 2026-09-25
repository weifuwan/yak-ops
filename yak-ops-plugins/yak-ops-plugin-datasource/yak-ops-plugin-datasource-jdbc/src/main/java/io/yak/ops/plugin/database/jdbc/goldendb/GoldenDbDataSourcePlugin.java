package io.yak.ops.plugin.database.jdbc.goldendb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.spi.datasource.DataSourceCatalog;
import java.util.Locale;

/** GoldenDB datasource plugin backed by the MySQL-compatible protocol and Connector/J. */
public final class GoldenDbDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public String type() {
        return "GOLDENDB";
    }

    @Override
    protected String displayName() {
        return "GoldenDB";
    }

    @Override
    protected java.util.Set<String> aliases() {
        return java.util.Set.of("GOLDEN_DB", "ZTE_GOLDENDB");
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:mysql://{host}:{port}/{database}";
    }

    @Override
    protected int defaultPort() {
        // GoldenDB deployment ports are configurable. Use the MySQL-compatible default as an editable
        // form fallback; an explicit JDBC URL or port always takes precedence.
        return 3306;
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

    @Override
    protected void appendNormalizedFields(JsonNode source, ObjectNode normalized) {
        // GoldenDB shares jdbc:mysql URLs with MySQL/TiDB, so the datasource identity must be restored
        // explicitly immediately before Link-Up execution.
        normalized.put("dialect", "goldendb");
    }

    /**
     * GoldenDB Stage 1 uses MySQL-compatible catalog, identifier and preview behavior while retaining
     * GOLDENDB as the user-facing datasource identity.
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
