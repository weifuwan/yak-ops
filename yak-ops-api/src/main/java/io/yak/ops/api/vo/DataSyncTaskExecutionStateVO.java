package io.yak.ops.api.vo;

import io.yak.ops.common.enums.WorkflowExecutionStatus;

import java.util.Date;

import lombok.Builder;
import lombok.Data;

/**
 * Latest execution snapshot for one data-sync task.
 * A missing workflow or task instance is a successful empty history result.
 */
@Data
@Builder
public class DataSyncTaskExecutionStateVO {

    private String taskCode;
    private WorkflowState workflow;
    private DataSyncInstanceVO taskInstance;

    @Data
    @Builder
    public static class WorkflowState {

        private Integer id;
        private Long workflowDefinitionCode;
        private WorkflowExecutionStatus state;
        private Date startTime;
        private Date endTime;
    }
}
