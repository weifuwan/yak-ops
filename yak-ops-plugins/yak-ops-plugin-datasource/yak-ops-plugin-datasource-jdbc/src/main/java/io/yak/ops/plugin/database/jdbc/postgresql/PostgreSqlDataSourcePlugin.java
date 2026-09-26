package io.yak.ops.plugin.database.jdbc.postgresql;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import java.util.Map;
import java.util.Set;

/**
 * PostgreSQL JDBC Provider，拥有 PostgreSQL 默认端口、Driver、URL、属性规则和兼容类型别名。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public final class PostgreSqlDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    private static final Map<String, String> PROPERTY_KEYS = Map.ofEntries(
            Map.entry("sslmode", "sslmode"),
            Map.entry("connecttimeout", "connectTimeout"),
            Map.entry("sockettimeout", "socketTimeout"),
            Map.entry("applicationname", "ApplicationName"),
            Map.entry("currentschema", "currentSchema"),
            Map.entry("tcpkeepalive", "tcpKeepAlive"),
            Map.entry("rewritebatchedinserts", "reWriteBatchedInserts"));

    private static final Set<String> SSL_MODES =
            Set.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full");

    @Override
    public String type() {
        return "POSTGRE_SQL";
    }

    @Override
    protected Set<String> aliases() {
        return Set.of("POSTGRESQL", "POSTGRES");
    }

    @Override
    protected int defaultPort() {
        return 5432;
    }

    @Override
    protected String defaultDriverClassName() {
        return "org.postgresql.Driver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    @Override
    protected Map<String, String> normalizeProperties(Map<String, String> properties) {
        Map<String, String> normalized = canonicalizeProperties(properties, PROPERTY_KEYS);
        normalizeLowerCaseEnumProperty(normalized, "sslmode", SSL_MODES);
        normalizeBooleanProperty(normalized, "tcpKeepAlive");
        normalizeBooleanProperty(normalized, "reWriteBatchedInserts");
        return normalized;
    }

    @Override
    protected void validateProperties(Map<String, String> properties) {
        validateNonNegativeIntegerProperty(properties, "connectTimeout");
        validateNonNegativeIntegerProperty(properties, "socketTimeout");
        validateNonBlankProperty(properties, "ApplicationName");
        validateNonBlankProperty(properties, "currentSchema");
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:postgresql:");
    }
}
