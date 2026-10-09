package io.yak.ops.dao.repository.datasync;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Data Sync Operations Dashboard 失败 / 丢失 Task 排名持久化读模型。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Data
public class DataSyncOperationsFailureStats {

    private String taskId;

    private String taskName;

    private Long failedCount;

    private Long lostCount;

    private Long abnormalCount;

    private LocalDateTime latestFailureTime;
}
