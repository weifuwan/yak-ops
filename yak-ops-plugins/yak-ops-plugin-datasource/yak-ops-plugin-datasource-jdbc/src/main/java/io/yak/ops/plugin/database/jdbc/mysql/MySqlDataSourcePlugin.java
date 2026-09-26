package io.yak.ops.plugin.database.jdbc.mysql;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.plugin.database.jdbc.mysql.enums.MySqlDriverId;
import io.yak.ops.plugin.database.jdbc.runtime.IsolatedJdbcDriverRuntime;
import java.sql.Connection;
import java.sql.DriverPropertyInfo;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * MySQL JDBC Provider，拥有 MySQL 默认端口、Driver、JDBC URL 和连接属性规则。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public final class MySqlDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    private static final IsolatedJdbcDriverRuntime DRIVER_RUNTIME = IsolatedJdbcDriverRuntime.getInstance();

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
        return MySqlDriverId.MYSQL_8.driverClassName();
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
        MySqlDriverId driverId = MySqlDriverId.MYSQL_8;
        return DRIVER_RUNTIME.propertyInfo(
                driverId.runtimeId(),
                driverId.driverDirectory(),
                driverId.driverClassName(),
                jdbcUrl,
                properties);
    }

    @Override
    protected String normalizeDriverId(JsonNode connectionJson) {
        String value = JSONUtils.firstText(connectionJson, "driverId");
        try {
            return MySqlDriverId.parse(value).name();
        } catch (IllegalArgumentException exception) {
            throw parameterError("MySQL driverId 仅支持 AUTO、MYSQL_8、MYSQL_5", exception);
        }
    }

    @Override
    protected String normalizeDriverClassName(JsonNode connectionJson, String driverId) {
        return MySqlDriverId.parse(driverId).driverClassName();
    }

    @Override
    protected Connection connectJdbc(JdbcConnectionProperties connection, String jdbcUrl, int timeoutSeconds)
            throws Exception {
        MySqlDriverId driverId = MySqlDriverId.parse(connection.driverId());
        Properties properties = connectionProperties(connection);
        properties.putIfAbsent("connectTimeout", String.valueOf(Math.max(1, timeoutSeconds) * 1000));
        return DRIVER_RUNTIME.connect(
                driverId.runtimeId(),
                driverId.driverDirectory(),
                driverId.driverClassName(),
                jdbcUrl,
                properties);
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
