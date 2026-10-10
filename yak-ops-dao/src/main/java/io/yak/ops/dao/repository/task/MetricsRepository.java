package io.yak.ops.dao.repository.task;

import java.time.LocalDateTime;

/**
 * 通用Task聚合指标只读Repository，不保存额外的Metrics事实表。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface MetricsRepository {

    MetricsStats querySummary(String workspaceId, String taskType, LocalDateTime start, LocalDateTime end);
}
