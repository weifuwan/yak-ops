package io.yak.ops.api.vo;

import io.yak.ops.dao.model.WorkflowDefinitionCountDto;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinitionCountVO {

    private int count;

    private List<WorkflowDefinitionCountDto> userList;

    public static WorkflowDefinitionCountVO empty() {
        return new WorkflowDefinitionCountVO(0, Collections.emptyList());
    }

    public static WorkflowDefinitionCountVO of(List<WorkflowDefinitionCountDto> workflowDefinitionCounts) {
        if (CollectionUtils.isEmpty(workflowDefinitionCounts)) {
            return empty();
        }
        WorkflowDefinitionCountVO workflowDefinitionCountVo = new WorkflowDefinitionCountVO();
        workflowDefinitionCountVo.setUserList(workflowDefinitionCounts);
        workflowDefinitionCountVo
                .setCount(workflowDefinitionCounts.stream().mapToInt(WorkflowDefinitionCountDto::getCount).sum());
        return workflowDefinitionCountVo;
    }

}
