package io.yak.ops.business.datasync.execution.planning;

import io.yak.ops.common.bean.vo.datasync.DataSyncOfflineRuntimePlanVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRuntimeConfigVO;
import java.util.Objects;

/**
 * OFFLINE Execution 创建时的有效运行参数与规划摘要。
 *
 * @param effectiveConfig 本次 Execution 实际冻结并执行的运行参数
 * @param summary 本次自动规划的可解释摘要
 * @author weifuwan
 * @since 2026-10-06
 */
public record OfflineRuntimePlan(DataSyncRuntimeConfigVO effectiveConfig, DataSyncOfflineRuntimePlanVO summary) {

    public OfflineRuntimePlan {
        Objects.requireNonNull(effectiveConfig, "effectiveConfig must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
    }
}
