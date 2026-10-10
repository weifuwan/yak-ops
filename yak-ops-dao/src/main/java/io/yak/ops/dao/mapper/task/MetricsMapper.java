package io.yak.ops.dao.mapper.task;

import io.yak.ops.dao.repository.task.MetricsStats;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 聚合Workspace范围内的通用Task Instance指标；从根实例计算，避免Attempt重复计数。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Mapper
public interface MetricsMapper {

    @Select("""
            <script>
            SELECT
                COUNT(*) AS instance_count,
                COALESCE(SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END), 0) AS succeeded_count,
                COALESCE(SUM(CASE WHEN status = 4 THEN 1 ELSE 0 END), 0) AS failed_count,
                COALESCE(SUM(CASE WHEN status = 6 THEN 1 ELSE 0 END), 0) AS lost_count,
                COALESCE(SUM(CASE WHEN status IN (1, 2, 7) THEN 1 ELSE 0 END), 0) AS active_count
            FROM yak_ops_task_instance
            WHERE workspace_id = #{workspaceId}
              AND create_time &gt;= #{startTime}
              AND create_time &lt; #{endTime}
            <if test="taskType != null">
              AND task_type = #{taskType}
            </if>
            </script>
            """)
    MetricsStats selectSummary(
            @Param("workspaceId") String workspaceId,
            @Param("taskType") String taskType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
