package io.yak.ops.plugin.database.jdbc.mysql;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import java.util.Map;
import java.util.Set;

/**
 * MySQL JDBC Provider，拥有 MySQL 默认端口、Driver、JDBC URL 和连接属性规则。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public final class MySqlDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    private static final Map<String, String> PROPERTY_KEYS = Map.ofEntries(
            Map.entry("useunicode", "useUnicode"),
            Map.entry("characterencoding", "characterEncoding"),
            Map.entry("servertimezone", "serverTimezone"),
            Map.entry("usessl", "useSSL"),
            Map.entry("sslmode", "sslMode"),
            Map.entry("requiressl", "requireSSL"),
            Map.entry("allowpublickeyretrieval", "allowPublicKeyRetrieval"),
            Map.entry("connecttimeout", "connectTimeout"),
            Map.entry("sockettimeout", "socketTimeout"),
            Map.entry("useaffectedrows", "useAffectedRows"),
            Map.entry("uselocaltransactionstate", "useLocalTransactionState"),
            Map.entry("enabledtlsprotocols", "enabledTLSProtocols"));

    private static final Set<String> SSL_MODES =
            Set.of("DISABLED", "PREFERRED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY");

    @Override
    public String type() {
        return "MYSQL";
    }

    @Override
    protected int defaultPort() {
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
    protected Map<String, String> normalizeProperties(Map<String, String> properties) {
        Map<String, String> normalized = canonicalizeProperties(properties, PROPERTY_KEYS);
        normalizeBooleanProperty(normalized, "useUnicode");
        normalizeBooleanProperty(normalized, "useSSL");
        normalizeBooleanProperty(normalized, "requireSSL");
        normalizeBooleanProperty(normalized, "allowPublicKeyRetrieval");
        normalizeBooleanProperty(normalized, "useAffectedRows");
        normalizeBooleanProperty(normalized, "useLocalTransactionState");
        normalizeUpperCaseEnumProperty(normalized, "sslMode", SSL_MODES);
        return normalized;
    }

    @Override
    protected void validateProperties(Map<String, String> properties) {
        validateNonNegativeIntegerProperty(properties, "connectTimeout");
        validateNonNegativeIntegerProperty(properties, "socketTimeout");
        validateNonBlankProperty(properties, "characterEncoding");
        validateNonBlankProperty(properties, "serverTimezone");
        validateNonBlankProperty(properties, "enabledTLSProtocols");
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:mysql:");
    }
}
