package io.yak.ops.api.vo;

import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.model.WorkflowInstanceStatusCountDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowInstanceCountVO {

    private int totalCount;

    private List<WorkflowInstanceStatusCountDto> workflowInstanceStatusCounts;

    public static WorkflowInstanceCountVO empty() {
        return of(Collections.emptyList());
    }

    public static WorkflowInstanceCountVO of(List<WorkflowInstanceStatusCountDto> workflowInstanceStatusCountDtos) {
        workflowInstanceStatusCountDtos = new ArrayList<>(workflowInstanceStatusCountDtos);

        Map<WorkflowExecutionStatus, WorkflowInstanceStatusCountDto> workflowExecutionStatusWorkflowInstanceStatusCountMap =
                workflowInstanceStatusCountDtos.stream()
                        .collect(Collectors.toMap(WorkflowInstanceStatusCountDto::getState, Function.identity()));
        for (WorkflowExecutionStatus workflowExecutionStatus : WorkflowExecutionStatus.values()) {
            if (!workflowExecutionStatusWorkflowInstanceStatusCountMap.containsKey(workflowExecutionStatus)) {
                workflowInstanceStatusCountDtos.add(new WorkflowInstanceStatusCountDto(workflowExecutionStatus, 0));
            }
        }

        WorkflowInstanceCountVO workflowInstanceCountVo = new WorkflowInstanceCountVO();
        workflowInstanceCountVo.setWorkflowInstanceStatusCounts(workflowInstanceStatusCountDtos);
        workflowInstanceCountVo.setTotalCount(
                workflowInstanceStatusCountDtos.stream().mapToInt(WorkflowInstanceStatusCountDto::getCount).sum());
        return workflowInstanceCountVo;
    }

}
