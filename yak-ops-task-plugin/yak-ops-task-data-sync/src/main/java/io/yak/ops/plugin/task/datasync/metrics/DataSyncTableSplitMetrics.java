package io.yak.ops.plugin.task.datasync.metrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Snapshot split progress reported by the source enumerator for one physical source table. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncTableSplitMetrics {

    private String tableIdentifier;

    private Long plannedSplits;

    private Long completedSplits;
}
