package io.yak.ops.dao.mapper.task;

import io.yak.ops.dao.repository.task.DefinitionPageQuery;
import java.util.Map;

/** Typed product filters target nested canonical JSON paths; bound filter values remain parameters. */
public final class DefinitionSqlProvider {
    /** Connector projection for recognized parameter formats; unknown shapes remain unclassified. */
    public static final String SOURCE_TYPE = """
            (CASE
              WHEN v.parameter_schema_version = 3 THEN
                CASE JSON_UNQUOTE(JSON_EXTRACT(v.parameters, '$.solutionType'))
                  WHEN 'SINGLE_TABLE_OFFLINE' THEN 'JDBC'
                  WHEN 'DATABASE_OFFLINE' THEN 'JDBC'
                  WHEN 'SINGLE_TABLE_REALTIME' THEN 'MYSQL_CDC'
                END
              WHEN v.parameter_schema_version = 2
                AND JSON_UNQUOTE(JSON_EXTRACT(v.parameters, '$.sink.type')) = 'JDBC'
                AND JSON_UNQUOTE(JSON_EXTRACT(v.parameters, '$.source.type')) IN ('JDBC', 'MYSQL_CDC')
              THEN JSON_UNQUOTE(JSON_EXTRACT(v.parameters, '$.source.type'))
            END)
            """;

    /** Product projection used before paging, including retained version-two definitions. */
    public static final String SOLUTION_TYPE = """
            (CASE
              WHEN v.parameter_schema_version = 3
                THEN JSON_UNQUOTE(JSON_EXTRACT(v.parameters, '$.solutionType'))
              WHEN v.parameter_schema_version = 2
                AND JSON_UNQUOTE(JSON_EXTRACT(v.parameters, '$.sink.type')) = 'JDBC'
              THEN CASE JSON_UNQUOTE(JSON_EXTRACT(v.parameters, '$.source.type'))
                WHEN 'JDBC' THEN 'SINGLE_TABLE_OFFLINE'
                WHEN 'MYSQL_CDC' THEN 'SINGLE_TABLE_REALTIME'
              END
            END)
            """;

    private static final Map<String, String> JSON_FILTER_PATHS = Map.of(
            "sourceDataSourceId", "$.source.datasourceId",
            "targetDataSourceId", "$.sink.datasourceId");

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
            if ("sourceType".equals(key)) {
                sql.append(" AND ").append(SOURCE_TYPE).append(" = #{filter.parameterFilters.sourceType}");
                continue;
            }
            if ("solutionType".equals(key)) {
                sql.append(" AND ").append(SOLUTION_TYPE).append(" = #{filter.parameterFilters.solutionType}");
                continue;
            }
            String path = JSON_FILTER_PATHS.get(key);
            if (path == null) throw new IllegalArgumentException("Unsupported definition parameter filter");
            sql.append(" AND JSON_UNQUOTE(JSON_EXTRACT(v.parameters, '")
                    .append(path)
                    .append("')) = #{filter.parameterFilters.")
                    .append(key)
                    .append("}");
        }
        return sql.append(" ORDER BY d.update_time DESC, d.id DESC").toString();
    }
}
