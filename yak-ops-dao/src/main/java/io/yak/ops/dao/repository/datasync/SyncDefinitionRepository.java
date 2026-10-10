package io.yak.ops.dao.repository.datasync;

import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.datasync.SyncDefinitionEntity;
import java.util.List;
import java.util.Optional;

/**
 * 定义 Workspace-scoped 数据同步任务定义持久化能力。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public interface SyncDefinitionRepository {
    /** 新任务定义与插件配置在同一事务内创建，返回已初始化的投影。 */
    SyncDefinitionEntity add(SyncDefinitionEntity entity);

    PageData<SyncDefinitionEntity> queryPage(String workspaceId, SyncDefinitionPageQuery query);

    Optional<SyncDefinitionEntity> queryById(String workspaceId, String id);

    List<SyncDefinitionEntity> queryRealtimeDesiredRunning();

    SyncDefinitionEntity update(String workspaceId, SyncDefinitionEntity entity);

    int deleteById(String workspaceId, String id);

    boolean existsByName(String workspaceId, String name, String excludeId);
}
