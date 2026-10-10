package io.yak.ops.dao.repository.task;

import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.task.InstanceEntity;
import java.util.Optional;

/**
 * 通用Task Instance的Workspace-scoped只读持久化接口。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface InstanceRepository {

    Optional<InstanceEntity> queryById(String workspaceId, String instanceId);

    PageData<InstanceEntity> queryPage(String workspaceId, InstancePageQuery query);
}
