package io.yak.ops.plugin.database.jdbc.mysql;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.auto.service.AutoService;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import java.sql.DriverPropertyInfo;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Defines MySQL provider defaults for ports, driver selection, JDBC URLs and connection properties.
 */
@AutoService(DataSourcePlugin.class)
public final class MySqlDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    private static final Option<String> DRIVER_ID = Options.key("driverId")
            .singleChoice(String.class, java.util.List.of("AUTO", "MYSQL_8", "MYSQL_5"))
            .defaultValue("AUTO");
    private static final Option<String> SSL_MODE = Options.key("properties.sslMode")
            .singleChoice(
                    String.class,
                    java.util.List.of("DISABLED", "PREFERRED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY"))
            .noDefaultValue();
    private static final Option<Integer> CONNECT_TIMEOUT =
            Options.key("properties.connectTimeout").intType().noDefaultValue();
    private static final Option<Integer> SOCKET_TIMEOUT =
            Options.key("properties.socketTimeout").intType().noDefaultValue();
    private static final Option<String> CHARACTER_ENCODING =
            Options.key("properties.characterEncoding").stringType().noDefaultValue();
    private static final Option<String> CONNECTION_TIMEZONE =
            Options.key("properties.connectionTimeZone").stringType().noDefaultValue();
    private static final Option<String> ENABLED_TLS =
            Options.key("properties.enabledTLSProtocols").stringType().noDefaultValue();
    private static final Option<Boolean> USE_UNICODE =
            Options.key("properties.useUnicode").booleanType().noDefaultValue();
    private static final Option<Boolean> USE_SSL =
            Options.key("properties.useSSL").booleanType().noDefaultValue();
    private static final Option<Boolean> REQUIRE_SSL =
            Options.key("properties.requireSSL").booleanType().noDefaultValue();
    private static final Option<Boolean> PUBLIC_KEY =
            Options.key("properties.allowPublicKeyRetrieval").booleanType().noDefaultValue();
    private static final Option<Boolean> AFFECTED_ROWS =
            Options.key("properties.useAffectedRows").booleanType().noDefaultValue();
    private static final Option<Boolean> LOCAL_TRANSACTIONS =
            Options.key("properties.useLocalTransactionState").booleanType().noDefaultValue();

    @Override
    protected OptionRule providerOptionRule() {
        return OptionRule.builder()
                .optional(
                        DRIVER_ID,
                        SSL_MODE,
                        USE_UNICODE,
                        USE_SSL,
                        REQUIRE_SSL,
                        PUBLIC_KEY,
                        AFFECTED_ROWS,
                        LOCAL_TRANSACTIONS)
                .optional(CONNECT_TIMEOUT, Conditions.greaterOrEqual(CONNECT_TIMEOUT, 0))
                .optional(SOCKET_TIMEOUT, Conditions.greaterOrEqual(SOCKET_TIMEOUT, 0))
                .optional(CHARACTER_ENCODING, Conditions.notBlank(CHARACTER_ENCODING))
                .optional(CONNECTION_TIMEZONE, Conditions.notBlank(CONNECTION_TIMEZONE))
                .optional(ENABLED_TLS, Conditions.notBlank(ENABLED_TLS))
                .build();
    }

    private static final Map<String, String> PROPERTY_KEYS = Map.ofEntries(
            Map.entry("useunicode", "useUnicode"),
            Map.entry("characterencoding", "characterEncoding"),
            Map.entry("connectiontimezone", "connectionTimeZone"),
            Map.entry("usessl", "useSSL"),
            Map.entry("sslmode", "sslMode"),
            Map.entry("requiressl", "requireSSL"),
            Map.entry("allowpublickeyretrieval", "allowPublicKeyRetrieval"),
            Map.entry("connecttimeout", "connectTimeout"),
            Map.entry("sockettimeout", "socketTimeout"),
            Map.entry("useaffectedrows", "useAffectedRows"),
            Map.entry("uselocaltransactionstate", "useLocalTransactionState"),
            Map.entry("enabledtlsprotocols", "enabledTLSProtocols"));

    @Override
    public String type() {
        return "MYSQL";
    }

    @Override
    public String displayJdbcUrl(DataSourceConnection connection) {
        return displayJdbcUrlWithQueryProperties(connection);
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
    protected Set<String> knownConnectionPropertyKeys() {
        return Set.copyOf(PROPERTY_KEYS.values());
    }

    @Override
    protected DriverPropertyInfo[] connectionPropertyInfo(String jdbcUrl, Properties properties) throws Exception {
        return connectionPropertyDriver(jdbcUrl).getPropertyInfo(jdbcUrl, properties);
    }

    @Override
    protected String resolveDriverId(JsonNode connectionJson) {
        String value = JSONUtils.firstText(connectionJson, "driverId");
        try {
            if (value == null || value.isBlank()) return "AUTO";
            String id = value.trim().toUpperCase(java.util.Locale.ROOT);
            if (!java.util.List.of("AUTO", "MYSQL_8", "MYSQL_5").contains(id)) {
                throw new IllegalArgumentException("Unsupported MySQL driver id");
            }
            return id;
        } catch (IllegalArgumentException exception) {
            throw parameterError("MySQL driverId 仅支持 AUTO、MYSQL_8、MYSQL_5", exception);
        }
    }

    @Override
    protected String resolveDriverClassName(JsonNode connectionJson, String driverId) {
        return "MYSQL_5".equals(driverId) ? "com.mysql.jdbc.Driver" : "com.mysql.cj.jdbc.Driver";
    }

    @Override
    protected Map<String, String> prepareConnectionProperties(Map<String, String> properties) {
        Map<String, String> normalized = canonicalizeProperties(properties, PROPERTY_KEYS);
        for (String key : java.util.List.of(
                "useUnicode",
                "useSSL",
                "requireSSL",
                "allowPublicKeyRetrieval",
                "useAffectedRows",
                "useLocalTransactionState")) {
            String value = normalized.get(key);
            if (value != null) {
                normalized.put(key, value.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        String sslMode = normalized.get("sslMode");
        if (sslMode != null) {
            normalized.put("sslMode", sslMode.trim().toUpperCase(java.util.Locale.ROOT));
        }
        return normalized;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:mysql:");
    }
}
