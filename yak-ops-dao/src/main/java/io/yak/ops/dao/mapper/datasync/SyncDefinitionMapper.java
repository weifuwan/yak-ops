package io.yak.ops.dao.mapper.datasync;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.dao.entity.datasync.SyncDefinitionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * DATA_SYNC 插件专属配置表的 MyBatis 映射及 Workspace-scoped Definition 联合索引查询。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Mapper
public interface SyncDefinitionMapper extends BaseMapper<SyncDefinitionEntity> {

    /** 先对完整定义+专属配置联合分页，再按 ID 批量读取，避免分页偏移和 N+1。 */
    @Select("""
            <script>
            SELECT sync.id
            FROM yak_ops_data_sync_task sync
            JOIN yak_ops_task_definition def
              ON def.id = sync.id AND def.workspace_id = sync.workspace_id
            WHERE def.workspace_id = #{workspaceId}
              AND def.task_type = 'DATA_SYNC'
            <if test="keyword != null and keyword != ''">
              AND (def.name LIKE CONCAT('%', #{keyword}, '%')
                OR sync.source_table LIKE CONCAT('%', #{keyword}, '%')
                OR sync.target_table LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="syncType != null">
              AND sync.sync_type = #{syncType}
            </if>
            <if test="status != null">
              AND def.status = #{status}
            </if>
            <if test="sourceId != null">
              AND sync.source_data_source_id = #{sourceId}
            </if>
            <if test="targetId != null">
              AND sync.target_data_source_id = #{targetId}
            </if>
            ORDER BY def.update_time DESC, def.id DESC
            </script>
            """)
    IPage<String> selectDefinitionPageIds(
            Page<String> page,
            @Param("workspaceId") String workspaceId,
            @Param("keyword") String keyword,
            @Param("syncType") Integer syncType,
            @Param("status") Integer status,
            @Param("sourceId") String sourceId,
            @Param("targetId") String targetId);

    /** 只供服务启动时恢复历史期望状态，按通用 Definition 发布状态过滤。 */
    @Select("""
            SELECT sync.id
            FROM yak_ops_data_sync_task sync
            JOIN yak_ops_task_definition def
              ON def.id = sync.id AND def.workspace_id = sync.workspace_id
            WHERE def.task_type = 'DATA_SYNC'
              AND def.status = 1
              AND sync.sync_type = 2
              AND sync.desired_state = 1
            ORDER BY sync.workspace_id, sync.id
            """)
    List<String> selectRealtimeDesiredRunningIds();
}
