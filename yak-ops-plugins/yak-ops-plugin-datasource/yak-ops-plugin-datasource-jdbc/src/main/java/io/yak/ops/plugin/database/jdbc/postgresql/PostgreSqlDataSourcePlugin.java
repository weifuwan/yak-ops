package io.yak.ops.plugin.database.jdbc.postgresql;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import java.util.Map;
import java.util.Set;

/**
 * PostgreSQL JDBC Provider，拥有默认端口、Driver、URL、属性规则和兼容类型别名；Schema 仅作为 Catalog 命名空间或 currentSchema 高级参数。
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
    protected String normalizeSchema(String schema, Map<String, String> properties) {
        String normalized = StringUtils.trimToNull(schema);
        if (normalized != null) {
            throw parameterError("PostgreSQL schema 不属于数据源连接字段，请通过 properties.currentSchema 配置", null);
        }
        return null;
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
