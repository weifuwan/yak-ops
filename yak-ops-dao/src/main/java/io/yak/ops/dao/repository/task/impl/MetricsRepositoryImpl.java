package io.yak.ops.dao.repository.task.impl;

import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.mapper.task.MetricsMapper;
import io.yak.ops.dao.repository.task.MetricsRepository;
import io.yak.ops.dao.repository.task.MetricsStats;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

/**
 * 基于通用Task Instance的指标聚合；不与DATA_SYNC专属行数指标混算。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Repository
public class MetricsRepositoryImpl implements MetricsRepository {

    @Resource
    private MetricsMapper metricsMapper;

    @Override
    public MetricsStats querySummary(String workspaceId, String taskType, LocalDateTime start, LocalDateTime end) {
        if (StringUtils.isBlank(workspaceId) || start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("Invalid Task metrics range");
        }
        MetricsStats stats = metricsMapper.selectSummary(workspaceId, taskType, start, end);
        return stats == null ? new MetricsStats() : stats;
    }
}
