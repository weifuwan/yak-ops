package io.yak.ops.api.dto.taskRelation;

import io.yak.ops.api.dto.PageQueryDto;
import io.yak.ops.dao.entity.WorkflowTaskRelation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * task relation query request
 */
@Schema(name = "TASK-RELATION-QUERY")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class TaskRelationFilterRequest extends PageQueryDto {

    @Schema(example = "1234567890123")
    private long workflowCode;

    @Schema(example = "1234567890123")
    private long preTaskCode;

    @Schema(example = "1234567890123")
    private long postTaskCode;

    public TaskRelationFilterRequest(long preTaskCode, long postTaskCode) {
        this.preTaskCode = preTaskCode;
        this.postTaskCode = postTaskCode;
    }

    public WorkflowTaskRelation convert2TaskDefinition() {
        WorkflowTaskRelation workflowTaskRelation = new WorkflowTaskRelation();
        if (this.workflowCode != 0L) {
            workflowTaskRelation.setWorkflowDefinitionCode(this.workflowCode);
        }
        if (this.preTaskCode != 0L) {
            workflowTaskRelation.setPreTaskCode(this.preTaskCode);
        }
        if (this.postTaskCode != 0L) {
            workflowTaskRelation.setPostTaskCode(this.postTaskCode);
        }
        return workflowTaskRelation;
    }
}
