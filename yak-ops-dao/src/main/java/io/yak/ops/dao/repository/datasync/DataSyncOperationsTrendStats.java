package io.yak.ops.dao.repository.datasync;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Data Sync Operations Dashboard 时间桶聚合持久化读模型。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Data
public class DataSyncOperationsTrendStats {

    private LocalDateTime bucketStart;

    private Long executionCount;

    private Long succeededCount;

    private Long failedCount;

    private Long lostCount;

    private Long autoRecoveryCount;

    private Long readRows;

    private Long writeRows;

    private Long averageDurationMillis;
}
