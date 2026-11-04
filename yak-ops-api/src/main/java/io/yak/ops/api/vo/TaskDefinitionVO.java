package io.yak.ops.api.vo;

import io.yak.ops.common.enums.TimeoutFlag;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.WorkflowTaskRelation;

import java.util.List;

import org.springframework.beans.BeanUtils;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class TaskDefinitionVO extends TaskDefinition {

    private List<WorkflowTaskRelation> workflowTaskRelationList;

    public TaskDefinitionVO() {
    }

    public TaskDefinitionVO(List<WorkflowTaskRelation> workflowTaskRelationList) {
        this.workflowTaskRelationList = workflowTaskRelationList;
    }

    public static TaskDefinitionVO fromTaskDefinition(TaskDefinition taskDefinition) {
        TaskDefinitionVO taskDefinitionVo = new TaskDefinitionVO();
        BeanUtils.copyProperties(taskDefinition, taskDefinitionVo);
        if (TimeoutFlag.CLOSE == taskDefinition.getTimeoutFlag()) {
            taskDefinitionVo.setTimeoutNotifyStrategy(null);
        }
        return taskDefinitionVo;
    }

}
