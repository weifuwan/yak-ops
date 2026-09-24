package io.yak.ops.plugin.database.jdbc.clickhouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormField;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** ClickHouse datasource plugin used for management/catalog while offline execution can be native. */
public final class ClickHouseDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public DataSourceDbType dbType() {
        return DataSourceDbType.CLICKHOUSE;
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:clickhouse://{host}:{port}/{database}";
    }

    @Override
    protected int defaultPort() {
        return 8123;
    }

    @Override
    protected String defaultDriverClassName() {
        return "com.clickhouse.jdbc.ClickHouseDriver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:clickhouse://" + host + ":" + port + "/" + database;
    }

    @Override
    protected void appendFormFields(List<FormField> fields) {
        fields.add(field("serverTimeZone", "服务端时区", "INPUT", "可选，例如 Asia/Shanghai", null, Collections.emptyList()));
    }

    @Override
    protected void appendNormalizedFields(JsonNode source, ObjectNode normalized) {
        JsonNode value = source == null ? null : source.get("serverTimeZone");
        if (value == null || value.isNull()) {
            value = source == null ? null : source.get("server_time_zone");
        }
        if (value != null && !value.isNull() && !value.asText().trim().isEmpty()) {
            normalized.put("serverTimeZone", value.asText().trim());
        }
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:clickhouse:");
    }
}
