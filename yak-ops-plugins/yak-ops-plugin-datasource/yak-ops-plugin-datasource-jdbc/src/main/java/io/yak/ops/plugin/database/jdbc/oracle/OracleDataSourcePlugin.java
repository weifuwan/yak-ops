package io.yak.ops.plugin.database.jdbc.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import java.util.Map;
import java.util.Set;

/**
 * Oracle JDBC Provider，拥有 Oracle 默认端口、Driver、Service Name JDBC URL 和连接属性规则。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public final class OracleDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    private static final Map<String, String> PROPERTY_KEYS = Map.of(
            "oracle.net.connect_timeout", "oracle.net.CONNECT_TIMEOUT",
            "oracle.jdbc.readtimeout", "oracle.jdbc.ReadTimeout",
            "defaultrowprefetch", "defaultRowPrefetch",
            "oracle.jdbc.fanenabled", "oracle.jdbc.fanEnabled");

    @Override
    public String type() {
        return "ORACLE";
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
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:oracle:thin:@//" + host + ":" + port + "/" + database;
    }

    @Override
    protected Set<String> knownConnectionPropertyKeys() {
        return Set.copyOf(PROPERTY_KEYS.values());
    }

    @Override
    protected Map<String, String> normalizeProperties(Map<String, String> properties) {
        Map<String, String> normalized = canonicalizeProperties(properties, PROPERTY_KEYS);
        normalizeBooleanProperty(normalized, "oracle.jdbc.fanEnabled");
        return normalized;
    }

    @Override
    protected void validateProperties(Map<String, String> properties) {
        validateNonNegativeIntegerProperty(properties, "oracle.net.CONNECT_TIMEOUT");
        validateNonNegativeIntegerProperty(properties, "oracle.jdbc.ReadTimeout");
        validatePositiveIntegerProperty(properties, "defaultRowPrefetch");
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:oracle:");
    }
}
