package io.yak.ops.dao.repository.task;

import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.repository.BaseRepository;
import java.util.List;

/**
 * 通用任务定义不可变版本快照 Repository。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface DefinitionVersionRepository extends BaseRepository<DefinitionVersionEntity> {

    List<DefinitionVersionEntity> queryByDefinition(String workspaceId, String definitionId);
}
