package io.yak.ops.business.task.metrics.impl;

import io.yak.ops.business.task.metrics.MetricsService;
import io.yak.ops.common.bean.vo.task.MetricsVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.repository.task.MetricsRepository;
import io.yak.ops.dao.repository.task.MetricsStats;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * 通用Task指标窗口和类型归一化，根Instance为唯一业务计数单位。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Service
public class MetricsServiceImpl implements MetricsService {

    @Resource
    private MetricsRepository metricsRepository;

    @Override
    public MetricsVO querySummary(String taskType, Integer days) {
        int range = days == null ? 7 : days;
        if (range < 1 || range > 31) throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        String type = StringUtils.trimToNull(taskType);
        if (type != null) type = type.toUpperCase(Locale.ROOT).replace('-', '_');
        LocalDateTime end = DateUtils.now();
        LocalDateTime start = end.minusDays(range);
        MetricsStats stats = metricsRepository.querySummary(WorkspaceContext.requireWorkspaceId(), type, start, end);
        MetricsVO result = BeanCopyUtils.copy(stats, MetricsVO.class);
        result.setTaskType(type);
        result.setRangeStart(start);
        result.setRangeEnd(end);
        result.setInstanceCount(zero(result.getInstanceCount()));
        result.setSucceededCount(zero(result.getSucceededCount()));
        result.setFailedCount(zero(result.getFailedCount()));
        result.setLostCount(zero(result.getLostCount()));
        result.setActiveCount(zero(result.getActiveCount()));
        return result;
    }

    private long zero(Long value) {
        return value == null ? 0L : value;
    }
}
