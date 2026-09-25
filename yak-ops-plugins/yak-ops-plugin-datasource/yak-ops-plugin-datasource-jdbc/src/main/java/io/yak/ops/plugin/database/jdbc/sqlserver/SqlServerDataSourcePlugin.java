package io.yak.ops.plugin.database.jdbc.sqlserver;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;

/** Microsoft SQL Server JDBC datasource plugin. */
public final class SqlServerDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public String type() {
        return "SQL_SERVER";
    }

    @Override
    protected String displayName() {
        return "SQL Server";
    }

    @Override
    protected java.util.Set<String> aliases() {
        return java.util.Set.of("SQLSERVER", "MSSQL");
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:sqlserver://{host}:{port};databaseName={database}";
    }

    @Override
    protected int defaultPort() {
        return 1433;
    }

    @Override
    protected String defaultDriverClassName() {
        return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + database;
    }

    @Override
    protected String inferDatabase(String jdbcUrl) {
        if (!hasText(jdbcUrl)) {
            return null;
        }
        for (String item : jdbcUrl.split(";")) {
            int separator = item.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = item.substring(0, separator).trim();
            if ("databaseName".equalsIgnoreCase(key) || "database".equalsIgnoreCase(key)) {
                String value = item.substring(separator + 1).trim();
                if (value.startsWith("{") && value.endsWith("}") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1)
                            .replace("}}", "}")
                            .trim();
                }
                return hasText(value) ? value : null;
            }
        }
        return null;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase(java.util.Locale.ROOT).startsWith("jdbc:sqlserver:");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
