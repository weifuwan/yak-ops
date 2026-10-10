package io.yak.ops.dao.repository.task;

import io.yak.ops.dao.entity.task.AttemptEntity;
import java.util.List;

/**
 * 通用Task Attempt的只读历史查询，运行时日志读取不在DAO职责中。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface AttemptRepository {

    List<AttemptEntity> queryByInstance(String workspaceId, String instanceId);
}
