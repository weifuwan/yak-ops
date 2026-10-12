package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.connector.jdbc.database.JdbcSchemaCompatibility;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.validation.DataSourceInfo;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.task.datasync.sink.JdbcSinkParameters;
import io.yak.ops.plugin.task.datasync.source.JdbcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.MySqlCdcSourceParameters;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * DATA_SYNC definition checks, independent of execution and checkpoint state.
 *
 * <p>Structure is checked once during parsing. Physical checks use Workspace-authorized
 * Datasource metadata and do not silently replace an explicit database or schema.
 */
final class SyncValidator {

    private SyncValidator() {}

    static void checkParameters(SyncParameters parameters) {
        if (parameters == null || parameters.source() == null || parameters.sink() == null) {
            throw new TaskPluginException("DATA_SYNC requires one source and one sink");
        }
        if (!(parameters.source() instanceof JdbcSourceParameters)
                && !(parameters.source() instanceof MySqlCdcSourceParameters)) {
            throw new TaskPluginException("Unsupported DATA_SYNC source");
        }
        if (!(parameters.sink() instanceof JdbcSinkParameters sink)) {
            throw new TaskPluginException("Unsupported DATA_SYNC sink");
        }
        if (!parameters.transform().isEmpty()) {
            throw new TaskPluginException("DATA_SYNC transforms are not supported yet");
        }
        checkTableFields(
                parameters.source().datasourceId(),
                parameters.source().database(),
                parameters.source().schema(),
                parameters.source().table());
        checkTableFields(sink.datasourceId(), sink.database(), sink.schema(), sink.table());
        if (sink.writeMode() == null || sink.writeMode() == DataSyncWriteMode.OVERWRITE) {
            throw new TaskPluginException("DATA_SYNC supports APPEND or UPSERT only");
        }
        if (parameters.source() instanceof MySqlCdcSourceParameters && sink.writeMode() != DataSyncWriteMode.UPSERT) {
            throw new TaskPluginException("MySQL CDC requires UPSERT to preserve change events");
        }
    }

    static void checkResources(SyncParameters parameters, TaskValidationContext context) {
        var source = parameters.source();
        var sink = (JdbcSinkParameters) parameters.sink();
        DataSourceInfo sourceInfo = context.dataSource(source.datasourceId());
        DataSourceInfo sinkInfo = context.dataSource(sink.datasourceId());

        if (source instanceof MySqlCdcSourceParameters && !"MYSQL".equals(sourceInfo.type())) {
            throw new TaskPluginException("MySQL CDC requires a MySQL datasource");
        }

        TableId sourceTable = tablePath(sourceInfo, source.database(), source.schema(), source.table());
        TableId sinkTable = tablePath(sinkInfo, sink.database(), sink.schema(), sink.table());
        TableSchema from = context.tableSchema(source.datasourceId(), sourceTable);
        TableSchema to = context.tableSchema(sink.datasourceId(), sinkTable);
        boolean requireKeys =
                source instanceof MySqlCdcSourceParameters || sink.writeMode() == DataSyncWriteMode.UPSERT;
        checkSchemaCompatibility(from, to, requireKeys);
    }

    private static void checkTableFields(String datasourceId, String database, String schema, String table) {
        requireText(datasourceId, 64, "datasourceId");
        requireText(table, 128, "table");
        optionalText(database, 128, "database");
        optionalText(schema, 128, "schema");
    }

    private static void requireText(String value, int maxLength, String field) {
        if (StringUtils.isBlank(value)) {
            throw new TaskPluginException("DATA_SYNC requires " + field);
        }
        optionalText(value, maxLength, field);
    }

    private static void optionalText(String value, int maxLength, String field) {
        if (value != null && value.length() > maxLength) {
            throw new TaskPluginException("DATA_SYNC " + field + " exceeds " + maxLength + " characters");
        }
    }

    private static TableId tablePath(DataSourceInfo info, String database, String schema, String table) {
        String selectedDatabase = chooseNamespace(info.database(), database, "database");
        String selectedSchema = chooseNamespace(info.schema(), schema, "schema");
        return new TableId(selectedDatabase, selectedSchema, table);
    }

    private static String chooseNamespace(String bound, String selected, String field) {
        if (StringUtils.isNotBlank(bound) && StringUtils.isNotBlank(selected) && !bound.equals(selected)) {
            throw new TaskPluginException("DATA_SYNC " + field + " differs from the bound Datasource");
        }
        if (StringUtils.isNotBlank(selected)) {
            return selected;
        }
        return bound;
    }

    static void checkSchemaCompatibility(TableSchema source, TableSchema target, boolean requireKeys) {
        Map<String, Column> sourceColumns = columns(source);
        Map<String, Column> targetColumns = columns(target);
        for (var entry : sourceColumns.entrySet()) {
            Column other = targetColumns.get(entry.getKey());
            if (other == null || !JdbcSchemaCompatibility.isCompatible(entry.getValue(), other)) {
                throw new TaskPluginException("Source and target table schemas are incompatible");
            }
        }
        for (var entry : targetColumns.entrySet()) {
            if (!sourceColumns.containsKey(entry.getKey()) && !entry.getValue().nullable()) {
                throw new TaskPluginException("Target contains an unmatched required column");
            }
        }
        if (requireKeys) {
            Set<String> sourceKeys = keys(source.primaryKeys());
            if (sourceKeys.isEmpty() || !sourceKeys.equals(keys(target.primaryKeys()))) {
                throw new TaskPluginException("UPSERT requires matching source and target primary keys");
            }
        }
    }

    private static Map<String, Column> columns(TableSchema schema) {
        Map<String, Column> result = new LinkedHashMap<>();
        for (Column column : schema.columns()) {
            if (result.putIfAbsent(column.name().toLowerCase(Locale.ROOT), column) != null) {
                throw new TaskPluginException("Ambiguous table column names");
            }
        }
        return result;
    }

    private static Set<String> keys(java.util.List<String> values) {
        Set<String> result = new HashSet<>();
        for (String key : values) {
            result.add(key.toLowerCase(Locale.ROOT));
        }
        return result;
    }
}
