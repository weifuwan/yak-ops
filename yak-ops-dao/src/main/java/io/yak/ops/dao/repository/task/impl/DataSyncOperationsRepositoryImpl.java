package io.yak.ops.dao.repository.task.impl;

import io.yak.ops.common.bean.vo.task.DataSyncOperationsVO;
import io.yak.ops.dao.mapper.task.DefinitionSqlProvider;
import io.yak.ops.dao.repository.task.DataSyncOperationsRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Database-side Instance metrics scoped by Workspace and the pinned Pipeline runtime mode. */
@Repository
public class DataSyncOperationsRepositoryImpl implements DataSyncOperationsRepository {

    private static final String INSTANCE_JOIN = """
            FROM yak_ops_task_instance i
            JOIN yak_ops_task_definition_version v
              ON v.workspace_id = i.workspace_id
             AND v.definition_id = i.definition_id
             AND v.version = i.definition_version
            """;

    private static final String INSTANCE_FILTER = """
            WHERE i.workspace_id = ?
              AND i.task_type = 'DATA_SYNC'
            """ + " AND " + DefinitionSqlProvider.RUNTIME_MODE + " = ? ";

    private static final String INSTANCE_SCOPE = INSTANCE_JOIN + INSTANCE_FILTER;

    private static final String OUTCOMES = """
            COUNT(*) AS execution_count,
            COALESCE(SUM(CASE WHEN i.status = 'FINISHED' THEN 1 ELSE 0 END), 0) AS succeeded_count,
            COALESCE(SUM(CASE WHEN i.status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failed_count,
            COALESCE(SUM(CASE WHEN i.status = 'CANCELED' THEN 1 ELSE 0 END), 0) AS canceled_count
            """;

    @Resource
    private JdbcTemplate jdbc;

    @Override
    public DataSyncOperationsVO.Summary summary(
            String workspaceId, String runtimeMode, LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT " + OUTCOMES + INSTANCE_SCOPE + " AND i.create_time >= ? AND i.create_time < ?";
        return jdbc.queryForObject(
                sql,
                (rs, row) -> new DataSyncOperationsVO.Summary(
                        rs.getLong("execution_count"),
                        rs.getLong("succeeded_count"),
                        rs.getLong("failed_count"),
                        rs.getLong("canceled_count"),
                        0),
                workspaceId,
                runtimeMode,
                start,
                end);
    }

    @Override
    public long runningCount(String workspaceId, String runtimeMode) {
        String sql = "SELECT COUNT(*) " + INSTANCE_SCOPE + " AND i.status = 'RUNNING'";
        Long count = jdbc.queryForObject(sql, Long.class, workspaceId, runtimeMode);
        return count == null ? 0 : count;
    }

    @Override
    public List<DataSyncOperationsVO.TrendPoint> trend(
            String workspaceId, String runtimeMode, LocalDateTime start, LocalDateTime end, boolean hourly) {
        String bucketExpression;
        if (hourly) {
            bucketExpression = "DATE_FORMAT(i.create_time, '%Y-%m-%d %H:00:00')";
        } else {
            bucketExpression = "DATE_FORMAT(i.create_time, '%Y-%m-%d')";
        }
        String sql = "SELECT " + bucketExpression + " AS bucket, " + OUTCOMES + INSTANCE_SCOPE
                + " AND i.create_time >= ? AND i.create_time < ?"
                + " GROUP BY bucket ORDER BY bucket";
        return jdbc.query(
                sql,
                (rs, row) -> new DataSyncOperationsVO.TrendPoint(
                        rs.getString("bucket"),
                        rs.getLong("execution_count"),
                        rs.getLong("succeeded_count"),
                        rs.getLong("failed_count"),
                        rs.getLong("canceled_count")),
                workspaceId,
                runtimeMode,
                start,
                end);
    }

    @Override
    public List<DataSyncOperationsVO.Failure> recentFailures(
            String workspaceId, String runtimeMode, LocalDateTime start, LocalDateTime end) {
        String sql = """
                SELECT i.id AS instance_id, i.definition_id, d.name AS task_name,
                       i.failure_code, i.create_time
                """ + INSTANCE_JOIN + """
                  LEFT JOIN yak_ops_task_definition d
                    ON d.workspace_id = i.workspace_id AND d.id = i.definition_id
                """ + INSTANCE_FILTER + """
                  AND i.status = 'FAILED'
                  AND i.create_time >= ? AND i.create_time < ?
                  ORDER BY i.create_time DESC, i.id DESC LIMIT 5
                """;
        return jdbc.query(
                sql,
                (rs, row) -> new DataSyncOperationsVO.Failure(
                        rs.getString("instance_id"),
                        rs.getString("definition_id"),
                        rs.getString("task_name"),
                        rs.getString("failure_code"),
                        rs.getTimestamp("create_time").toLocalDateTime()),
                workspaceId,
                runtimeMode,
                start,
                end);
    }
}
