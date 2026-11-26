package io.yak.ops.plugin.task.datasync.execution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A completed Flink checkpoint that can be used to restore a new execution. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncRecoveryPointSnapshot {

    private String recoveryPointId;

    private String checkpointId;

    private String recoveryType;

    private String restorePath;

    private long completedAt;
}
