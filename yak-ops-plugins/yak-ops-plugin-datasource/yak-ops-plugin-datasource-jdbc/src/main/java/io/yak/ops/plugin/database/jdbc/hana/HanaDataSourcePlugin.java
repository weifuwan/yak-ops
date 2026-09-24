package io.yak.ops.plugin.database.jdbc.hana;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.GenericJdbcCatalog;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.spi.datasource.DataSourceCatalog;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** SAP HANA JDBC datasource plugin backed by SAP ngdbc. */
public final class HanaDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    private static final String URL_PREFIX = "jdbc:sap://";

    @Override
    public DataSourceDbType dbType() {
        return DataSourceDbType.HANA;
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:sap://{host}:{port}/?databaseName={database}";
    }

    @Override
    protected int defaultPort() {
        // 30015 is the common SQL port for instance 00 / the initial tenant.
        // Additional tenant databases can use other SQL ports, so the form keeps this editable.
        return 30015;
    }

    @Override
    protected String defaultDriverClassName() {
        return "com.sap.db.jdbc.Driver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return URL_PREFIX + host + ":" + port + "/?databaseName=" + database;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.trim().toLowerCase(Locale.ROOT).startsWith(URL_PREFIX);
    }

    @Override
    protected String inferDatabase(String jdbcUrl) {
        if (!acceptsUrl(jdbcUrl)) {
            return null;
        }
        int queryStart = jdbcUrl.indexOf('?');
        if (queryStart < 0 || queryStart == jdbcUrl.length() - 1) {
            return null;
        }
        String query = jdbcUrl.substring(queryStart + 1);
        for (String item : query.split("&")) {
            int equals = item.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = decode(item.substring(0, equals));
            if (!"databaseName".equalsIgnoreCase(key.trim())) {
                continue;
            }
            String value = decode(item.substring(equals + 1)).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    @Override
    protected void appendNormalizedFields(JsonNode source, ObjectNode normalized) {
        // Keep datasource-owned dialect identity next to the connection parameters. Offline JobSpec
        // strips it while editing and resolves it again immediately before Link-Up submission.
        normalized.put("dialect", "hana");
    }

    @Override
    protected Properties connectionProperties(JdbcConnectionProperties connection) {
        Properties properties = super.connectionProperties(connection);
        if (connection.schema() != null
                && !connection.schema().trim().isEmpty()
                && !containsKeyIgnoreCase(properties, "currentSchema")) {
            properties.setProperty("currentSchema", connection.schema().trim());
        }
        return properties;
    }

    /**
     * HANA metadata is schema-scoped; tenant database selection belongs to the JDBC connection.
     * System schemas are hidden from the normal datasource browser to keep table selection usable.
     */
    @Override
    protected DataSourceCatalog createJdbcCatalog(
            JdbcConnectionProperties connection, int connectionTimeoutSeconds, int queryTimeoutSeconds) {
        return new GenericJdbcCatalog(connection, connectionTimeoutSeconds, queryTimeoutSeconds) {
            @Override
            public List<String> listDatabases() {
                String database = connection.database();
                return database == null || database.trim().isEmpty() ? List.of() : List.of(database.trim());
            }

            @Override
            protected Connection openConnection() throws Exception {
                return HanaDataSourcePlugin.this.openJdbcConnection(connection, connectionTimeoutSeconds);
            }

            @Override
            protected boolean includeSchema(String schema) {
                if (!super.includeSchema(schema)) {
                    return false;
                }
                String normalized = schema.trim().toUpperCase(Locale.ROOT);
                return !"SYS".equals(normalized)
                        && !normalized.startsWith("_SYS_")
                        && !"SAP_HANA_INTERNAL".equals(normalized);
            }
        };
    }

    private static boolean containsKeyIgnoreCase(Properties properties, String key) {
        for (Object propertyKey : properties.keySet()) {
            if (propertyKey != null && key.equalsIgnoreCase(propertyKey.toString())) {
                return true;
            }
        }
        return false;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return value;
        }
    }
}
