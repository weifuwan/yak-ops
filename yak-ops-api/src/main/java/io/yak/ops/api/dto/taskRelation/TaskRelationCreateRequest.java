package io.yak.ops.api.dto.taskRelation;

import io.yak.ops.dao.entity.WorkflowTaskRelation;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class TaskRelationCreateRequest {

    @Schema(example = "12345678")
    private long projectCode;

    @Schema(example = "87654321", required = true)
    private long workflowCode;

    @Schema(example = "12345", required = true)
    private long preTaskCode;

    @Schema(example = "54321", required = true)
    private long postTaskCode;

    public WorkflowTaskRelation convert2WorkflowTaskRelation() {
        WorkflowTaskRelation workflowTaskRelation = new WorkflowTaskRelation();

        workflowTaskRelation.setProjectCode(this.projectCode);
        workflowTaskRelation.setWorkflowDefinitionCode(this.workflowCode);
        workflowTaskRelation.setPreTaskCode(this.preTaskCode);
        workflowTaskRelation.setPostTaskCode(this.postTaskCode);

        Date date = new Date();
        workflowTaskRelation.setCreateTime(date);
        workflowTaskRelation.setUpdateTime(date);
        return workflowTaskRelation;
    }
}
