package io.yak.ops.dao.repository.workspace;

import io.yak.ops.dao.entity.workspace.WorkspaceEntity;
import io.yak.ops.dao.repository.BaseRepository;
import java.util.List;

/**
 * Workspace 持久化边界。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
public interface WorkspaceEntityRepository extends BaseRepository<WorkspaceEntity> {

    List<WorkspaceEntity> queryByIds(List<String> ids);
}
