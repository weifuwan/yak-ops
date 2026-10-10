package io.yak.ops.dao.repository.task;

import io.yak.ops.dao.entity.task.EventEntity;
import java.util.List;

/**
 * 通用Task Instance生命周期事件读取；不代表真实Worker日志文件。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface EventRepository {

    List<EventEntity> queryByInstance(String workspaceId, String instanceId);
}
