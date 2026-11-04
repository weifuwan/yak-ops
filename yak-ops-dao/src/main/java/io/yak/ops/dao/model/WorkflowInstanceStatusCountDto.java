package io.yak.ops.dao.model;

import io.yak.ops.common.enums.WorkflowExecutionStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowInstanceStatusCountDto {

    private WorkflowExecutionStatus state;

    private int count;

}
