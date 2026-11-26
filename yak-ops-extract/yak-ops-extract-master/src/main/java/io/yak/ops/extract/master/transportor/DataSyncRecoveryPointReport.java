package io.yak.ops.extract.master.transportor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Worker-reported metadata for a completed, externally-restorable Flink recovery point. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncRecoveryPointReport {

    private String recoveryPointId;

    private String checkpointId;

    private String recoveryType;

    private String restorePath;

    private Long completedAt;
}
