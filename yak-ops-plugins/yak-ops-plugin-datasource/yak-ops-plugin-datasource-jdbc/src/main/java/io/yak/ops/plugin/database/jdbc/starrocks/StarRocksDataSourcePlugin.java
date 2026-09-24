package io.yak.ops.plugin.database.jdbc.starrocks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormField;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** StarRocks datasource management over the MySQL-compatible query port. */
public final class StarRocksDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public DataSourceDbType dbType() {
        return DataSourceDbType.STARROCKS;
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:mysql://{host}:{port}/{database}";
    }

    @Override
    protected int defaultPort() {
        return 9030;
    }

    @Override
    protected String defaultDriverClassName() {
        return "com.mysql.cj.jdbc.Driver";
    }

    @Override
    protected String databaseLabel() {
        return "StarRocks 数据库";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database;
    }

    @Override
    protected void appendFormFields(List<FormField> fields) {
        fields.add(field(
                "nodeUrls", "FE HTTP 节点", "INPUT", "可选；默认使用主机地址:8030，多个自定义节点使用英文逗号分隔", null, Collections.emptyList()));
    }

    @Override
    protected void appendNormalizedFields(JsonNode source, ObjectNode normalized) {
        JsonNode value = source == null ? null : source.get("nodeUrls");
        if (value == null || value.isNull()) {
            value = source == null ? null : source.get("node_urls");
        }
        if (value != null && !value.isNull() && !value.asText().trim().isEmpty()) {
            normalized.put("nodeUrls", value.asText().trim());
        }
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:mysql:");
    }
}
