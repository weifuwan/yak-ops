package io.yak.ops.plugin.database.jdbc.oceanbase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FieldType;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormField;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormOption;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import io.yak.ops.spi.datasource.DataSourcePluginException.Operation;
import java.util.List;
import java.util.Locale;

/** OceanBase JDBC datasource plugin with explicit MySQL/Oracle compatibility mode. */
public final class OceanBaseDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    @Override
    public DataSourceDbType dbType() {
        return DataSourceDbType.OCEANBASE;
    }

    @Override
    protected String jdbcUrlTemplate() {
        return "jdbc:oceanbase://{host}:{port}/{database}";
    }

    @Override
    protected int defaultPort() {
        return 2881;
    }

    @Override
    protected String defaultDriverClassName() {
        return "com.oceanbase.jdbc.Driver";
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return "jdbc:oceanbase://" + host + ":" + port + "/" + database;
    }

    @Override
    protected void appendFormFields(List<FormField> fields) {
        fields.add(new FormField(
                "compatibleMode",
                "兼容模式",
                FieldType.SELECT,
                "请选择 OceanBase 租户兼容模式",
                "mysql",
                List.of(new FormOption("MySQL", "mysql"), new FormOption("Oracle", "oracle")),
                required("请选择 OceanBase 兼容模式"),
                List.of(),
                List.of(),
                null));
    }

    @Override
    protected void appendNormalizedFields(JsonNode source, ObjectNode normalized) {
        JsonNode value = source == null ? null : source.get("compatibleMode");
        if (value == null || value.isNull()) {
            value = source == null ? null : source.get("compatible_mode");
        }
        String mode = value == null || value.isNull() ? "mysql" : value.asText("mysql");
        mode = mode.trim().toLowerCase(Locale.ROOT);
        if (!"mysql".equals(mode) && !"oracle".equals(mode)) {
            throw new DataSourcePluginException(Operation.PARAMETER, "OceanBase 兼容模式仅支持 mysql 或 oracle");
        }
        normalized.put("compatibleMode", mode);
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:oceanbase:");
    }
}
