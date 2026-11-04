package io.yak.ops.dao.entity;

import java.util.List;

import lombok.Data;

@Data
public class DagData {

    private WorkflowDefinition workflowDefinition;

    private List<WorkflowTaskRelation> workflowTaskRelationList;

    private List<TaskDefinition> taskDefinitionList;

    public DagData(WorkflowDefinition workflowDefinition, List<WorkflowTaskRelation> workflowTaskRelationList,
                   List<TaskDefinition> taskDefinitionList) {
        this.workflowDefinition = workflowDefinition;
        this.workflowTaskRelationList = workflowTaskRelationList;
        this.taskDefinitionList = taskDefinitionList;
    }

    public DagData() {
    }
}
