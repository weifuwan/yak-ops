package io.yak.ops.dao.repository.task;

import io.yak.ops.common.enums.task.ScheduleTargetType;
import io.yak.ops.dao.entity.task.ScheduleEntity;
import java.util.Optional;

/**
 * 通用Schedule的只读目标查询；Quartz触发注册仍由当前DATA_SYNC业务拥有。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface ScheduleRepository {

    Optional<ScheduleEntity> queryByTarget(String workspaceId, ScheduleTargetType type, String targetId);
}
