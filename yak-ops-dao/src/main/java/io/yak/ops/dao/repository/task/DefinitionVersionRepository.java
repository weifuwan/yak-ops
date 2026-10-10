package io.yak.ops.dao.repository.task;

import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import java.util.List;

/**
 * 通用任务定义的不可变历史版本持久化能力；不提供更新和删除历史版本的入口。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface DefinitionVersionRepository {

    /** 仅添加新的可执行版本，已有 (Definition ID, Version) 由数据库唯一约束保护。 */
    DefinitionVersionEntity append(DefinitionVersionEntity version);

    /** 返回当前 Workspace 可访问的真实历史版本，不重建不存在的旧版本。 */
    List<DefinitionVersionEntity> queryByDefinition(String workspaceId, String definitionId);
}
