package io.yak.ops.dao.repository.task;

import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.repository.BaseRepository;
import java.util.List;
import java.util.Optional;

/**
 * Workspace-scoped 通用任务定义持久化接口。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface DefinitionRepository extends BaseRepository<DefinitionEntity> {

    Optional<DefinitionEntity> queryById(String workspaceId, String id);

    List<DefinitionEntity> queryByIds(String workspaceId, List<String> ids);

    /** 仅供系统启动恢复使用，不能用作 HTTP 跨 Workspace 查询。 */
    List<DefinitionEntity> queryByIdsForRecovery(List<String> ids);

    DefinitionEntity update(String workspaceId, DefinitionEntity entity);

    int deleteById(String workspaceId, String id);

    boolean existsByName(String workspaceId, String name, String excludeId);
}
