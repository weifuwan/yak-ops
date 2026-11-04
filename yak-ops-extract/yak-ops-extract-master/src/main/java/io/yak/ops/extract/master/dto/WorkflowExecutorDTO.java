package io.yak.ops.extract.master.dto;

import io.yak.ops.common.enums.WorkflowExecutionStatus;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowExecutorDTO {

    private int id;

    private String name;

    private long projectCode;

    private long workflowDefinitionCode;

    private WorkflowExecutionStatus state;

    private Date startTime;

    private int runTimes;

}
