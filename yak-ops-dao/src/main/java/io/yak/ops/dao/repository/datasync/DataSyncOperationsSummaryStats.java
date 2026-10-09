package io.yak.ops.dao.repository.datasync;

import lombok.Data;

/**
 * Data Sync Operations Dashboard 聚合摘要持久化读模型。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Data
public class DataSyncOperationsSummaryStats {

    private Long executionCount;

    private Long succeededCount;

    private Long failedCount;

    private Long lostCount;

    private Long abnormalTaskCount;

    private Long currentActiveTaskCount;

    private Long autoRecoveryCount;

    private Long readRows;

    private Long writeRows;

    private Long averageDurationMillis;
}
