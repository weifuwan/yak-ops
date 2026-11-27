package io.yak.ops.extract.master.transportor;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Stage-level observation for one streaming CDC execution. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncExecutionSummary {

    private Integer version;

    private String observedAt;

    private Integer generation;

    private boolean restored;

    private String counterScope;

    private String phase;

    private Checkpoints checkpoints;

    private List<StageResult> stages;

    private List<TableStages> tables;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Checkpoints {

        private Long handoffId;

        private Long completedId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageResult {

        private String step;

        private String status;

        private Integer progressPercent;

        private String progressUnit;

        private Long readRows;

        private Long committedRows;

        private Long plannedSplits;

        private Long completedSplits;

        private String failureCode;

        private Integer plannedTables;

        private Integer completedTables;

        private String action;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableStages {

        private Integer mappingIndex;

        private List<StageResult> stages;
    }
}
