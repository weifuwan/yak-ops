package io.yak.ops.dao.mapper.datasync;

import io.yak.ops.dao.repository.datasync.DataSyncOperationsFailureStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsStatusStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsSummaryStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsTrendStats;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 提供 Data Sync Operations Dashboard 聚合 SQL。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Mapper
public interface DataSyncOperationsMetricsMapper {

    @Select("""
            SELECT
                COUNT(*) AS execution_count,
                COALESCE(SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END), 0) AS succeeded_count,
                COALESCE(SUM(CASE WHEN status = 4 THEN 1 ELSE 0 END), 0) AS failed_count,
                COALESCE(SUM(CASE WHEN status = 6 THEN 1 ELSE 0 END), 0) AS lost_count,
                COUNT(DISTINCT CASE WHEN status IN (4, 6) THEN task_id END) AS abnormal_task_count,
                (
                    SELECT COUNT(DISTINCT active.task_id)
                    FROM yak_ops_data_sync_instance active
                    WHERE active.workspace_id = #{workspaceId}
                      AND active.sync_type = #{syncType}
                      AND active.status IN (1, 2, 7)
                ) AS current_active_task_count,
                COALESCE(SUM(CASE WHEN trigger_type = 4 THEN 1 ELSE 0 END), 0) AS auto_recovery_count,
                COALESCE(SUM(read_rows), 0) AS read_rows,
                COALESCE(SUM(write_rows), 0) AS write_rows,
                CAST(COALESCE(AVG(
                    CASE
                        WHEN start_time IS NOT NULL AND finish_time IS NOT NULL
                        THEN TIMESTAMPDIFF(MICROSECOND, start_time, finish_time) / 1000
                        ELSE NULL
                    END
                ), 0) AS UNSIGNED) AS average_duration_millis
            FROM yak_ops_data_sync_instance
            WHERE workspace_id = #{workspaceId}
              AND sync_type = #{syncType}
              AND create_time >= #{startTime}
              AND create_time < #{endTime}
            """)
    DataSyncOperationsSummaryStats selectSummary(
            @Param("workspaceId") String workspaceId,
            @Param("syncType") Integer syncType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("""
            SELECT
                STR_TO_DATE(
                    DATE_FORMAT(create_time, '%Y-%m-%d %H:00:00'),
                    '%Y-%m-%d %H:%i:%s'
                ) AS bucket_start,
                COUNT(*) AS execution_count,
                COALESCE(SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END), 0) AS succeeded_count,
                COALESCE(SUM(CASE WHEN status = 4 THEN 1 ELSE 0 END), 0) AS failed_count,
                COALESCE(SUM(CASE WHEN status = 6 THEN 1 ELSE 0 END), 0) AS lost_count,
                COALESCE(SUM(CASE WHEN trigger_type = 4 THEN 1 ELSE 0 END), 0) AS auto_recovery_count,
                COALESCE(SUM(read_rows), 0) AS read_rows,
                COALESCE(SUM(write_rows), 0) AS write_rows,
                CAST(COALESCE(AVG(
                    CASE
                        WHEN start_time IS NOT NULL AND finish_time IS NOT NULL
                        THEN TIMESTAMPDIFF(MICROSECOND, start_time, finish_time) / 1000
                        ELSE NULL
                    END
                ), 0) AS UNSIGNED) AS average_duration_millis
            FROM yak_ops_data_sync_instance
            WHERE workspace_id = #{workspaceId}
              AND sync_type = #{syncType}
              AND create_time >= #{startTime}
              AND create_time < #{endTime}
            GROUP BY bucket_start
            ORDER BY bucket_start ASC
            """)
    List<DataSyncOperationsTrendStats> selectHourlyTrend(
            @Param("workspaceId") String workspaceId,
            @Param("syncType") Integer syncType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("""
            SELECT
                STR_TO_DATE(
                    DATE_FORMAT(create_time, '%Y-%m-%d 00:00:00'),
                    '%Y-%m-%d %H:%i:%s'
                ) AS bucket_start,
                COUNT(*) AS execution_count,
                COALESCE(SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END), 0) AS succeeded_count,
                COALESCE(SUM(CASE WHEN status = 4 THEN 1 ELSE 0 END), 0) AS failed_count,
                COALESCE(SUM(CASE WHEN status = 6 THEN 1 ELSE 0 END), 0) AS lost_count,
                COALESCE(SUM(CASE WHEN trigger_type = 4 THEN 1 ELSE 0 END), 0) AS auto_recovery_count,
                COALESCE(SUM(read_rows), 0) AS read_rows,
                COALESCE(SUM(write_rows), 0) AS write_rows,
                CAST(COALESCE(AVG(
                    CASE
                        WHEN start_time IS NOT NULL AND finish_time IS NOT NULL
                        THEN TIMESTAMPDIFF(MICROSECOND, start_time, finish_time) / 1000
                        ELSE NULL
                    END
                ), 0) AS UNSIGNED) AS average_duration_millis
            FROM yak_ops_data_sync_instance
            WHERE workspace_id = #{workspaceId}
              AND sync_type = #{syncType}
              AND create_time >= #{startTime}
              AND create_time < #{endTime}
            GROUP BY bucket_start
            ORDER BY bucket_start ASC
            """)
    List<DataSyncOperationsTrendStats> selectDailyTrend(
            @Param("workspaceId") String workspaceId,
            @Param("syncType") Integer syncType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("""
            SELECT status, COUNT(*) AS count
            FROM yak_ops_data_sync_instance
            WHERE workspace_id = #{workspaceId}
              AND sync_type = #{syncType}
              AND create_time >= #{startTime}
              AND create_time < #{endTime}
            GROUP BY status
            ORDER BY status ASC
            """)
    List<DataSyncOperationsStatusStats> selectStatusDistribution(
            @Param("workspaceId") String workspaceId,
            @Param("syncType") Integer syncType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("""
            SELECT
                i.task_id,
                (
                    SELECT latest.task_name
                    FROM yak_ops_data_sync_instance latest
                    WHERE latest.workspace_id = #{workspaceId}
                      AND latest.sync_type = #{syncType}
                      AND latest.task_id = i.task_id
                      AND latest.status IN (4, 6)
                      AND latest.create_time >= #{startTime}
                      AND latest.create_time < #{endTime}
                    ORDER BY latest.create_time DESC, latest.id DESC
                    LIMIT 1
                ) AS task_name,
                COALESCE(SUM(CASE WHEN i.status = 4 THEN 1 ELSE 0 END), 0) AS failed_count,
                COALESCE(SUM(CASE WHEN i.status = 6 THEN 1 ELSE 0 END), 0) AS lost_count,
                COUNT(*) AS abnormal_count,
                MAX(COALESCE(i.finish_time, i.create_time)) AS latest_failure_time
            FROM yak_ops_data_sync_instance i
            WHERE i.workspace_id = #{workspaceId}
              AND i.sync_type = #{syncType}
              AND i.status IN (4, 6)
              AND i.create_time >= #{startTime}
              AND i.create_time < #{endTime}
            GROUP BY i.task_id
            ORDER BY abnormal_count DESC, latest_failure_time DESC, i.task_id ASC
            LIMIT #{limit}
            """)
    List<DataSyncOperationsFailureStats> selectFailureRanking(
            @Param("workspaceId") String workspaceId,
            @Param("syncType") Integer syncType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") Integer limit);
}
