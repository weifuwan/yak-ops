package io.yak.ops.business.task.metrics;

import io.yak.ops.common.bean.vo.task.MetricsVO;

/**
 * 统一Task根实例指标查询；数据同步读写行数仍归DATA_SYNC指标读模型。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface MetricsService {

    /** 统计最近N天的根实例数；days范围1至31，taskType为空表示全部类型。 */
    MetricsVO querySummary(String taskType, Integer days);
}
