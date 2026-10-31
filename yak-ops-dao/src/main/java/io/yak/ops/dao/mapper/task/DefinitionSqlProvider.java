package io.yak.ops.dao.mapper.task;

import io.yak.ops.dao.repository.task.DefinitionPageQuery;
import java.util.Map;

/** Typed product filters target nested canonical JSON paths; bound filter values remain parameters. */
public final class DefinitionSqlProvider {
    /** Runtime mode used by product views independently of the selected Connector family. */
    public static final String RUNTIME_MODE = indexed("runtimeMode");

    private static final Map<String, String> JSON_FILTER_PATHS = Map.of(
            "sourceType", "sourceType",
            "sinkType", "sinkType",
            "runtimeMode", "runtimeMode",
            "tableScope", "tableScope",
            "sourceDataSourceId", "sourceDataSourceId",
            "targetDataSourceId", "targetDataSourceId");

    // A bounded CDC snapshot remains replication. The effective scope is derived by the Task plugin.
    private static final String DATA_SYNC_PROFILE = """
            (CASE WHEN v.parameter_schema_version = 7 THEN
                CASE WHEN JSON_CONTAINS_PATH(v.parameters, 'one', '$.source.startup') = 1
                          OR JSON_UNQUOTE(JSON_EXTRACT(v.parameters, '$.index.runtimeMode')) = 'STREAMING'
                     THEN 'DATABASE_REPLICATION'
                     WHEN JSON_UNQUOTE(JSON_EXTRACT(v.parameters, '$.index.tableScope')) = 'SINGLE'
                     THEN 'SINGLE_TABLE_BATCH'
                END
            END)
            """;

    private static String indexed(String field) {
        return "(CASE WHEN v.parameter_schema_version = 7 THEN JSON_UNQUOTE(JSON_EXTRACT(v.parameters, '$.index."
                + field + "')) END)";
    }

    private DefinitionSqlProvider() {}

    public static String selectDefinitionPage(Map<String, Object> args) {
        DefinitionPageQuery filter = (DefinitionPageQuery) args.get("filter");
        StringBuilder sql = new StringBuilder("""
                SELECT d.* FROM yak_ops_task_definition d
                JOIN yak_ops_task_definition_version v
                  ON v.workspace_id = d.workspace_id
                 AND v.definition_id = d.id AND v.version = d.current_version
                WHERE d.workspace_id = #{filter.workspaceId} AND d.deleted_at IS NULL
                """);
        if (filter.taskType() != null) sql.append(" AND d.task_type = #{filter.taskType}");
        if (filter.statusValue() != null) sql.append(" AND d.status = #{filter.statusValue}");
        if (filter.keyword() != null) {
            sql.append(" AND (d.name LIKE CONCAT('%', #{filter.keyword}, '%')");
            sql.append(" OR v.parameters LIKE CONCAT('%', #{filter.keyword}, '%'))");
        }
        for (String key : filter.parameterFilters().keySet()) {
            if ("dataSyncProfile".equals(key)) {
                sql.append(" AND d.task_type = 'DATA_SYNC' AND ")
                        .append(DATA_SYNC_PROFILE)
                        .append(" = #{filter.parameterFilters.dataSyncProfile}");
                continue;
            }
            String field = JSON_FILTER_PATHS.get(key);
            if (field == null) throw new IllegalArgumentException("Unsupported definition parameter filter");
            sql.append(" AND ")
                    .append(indexed(field))
                    .append(" = #{filter.parameterFilters.")
                    .append(key)
                    .append("}");
        }
        return sql.append(" ORDER BY d.update_time DESC, d.id DESC").toString();
    }
}
