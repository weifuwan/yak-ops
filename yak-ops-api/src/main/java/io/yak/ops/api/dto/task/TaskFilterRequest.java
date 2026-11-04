package io.yak.ops.api.dto.task;

import io.yak.ops.api.dto.PageQueryDto;
import io.yak.ops.dao.entity.TaskDefinition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * task query request
 */
@Schema(name = "TASK-QUERY")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class TaskFilterRequest extends PageQueryDto {

    @Schema(example = "project-name")
    private String projectName;

    @Schema(example = "task-name")
    private String name;

    @Schema(example = "SHELL")
    private String taskType;

    public TaskDefinition convert2TaskDefinition() {
        TaskDefinition taskDefinition = new TaskDefinition();
        if (this.projectName != null) {
            taskDefinition.setProjectName(this.projectName);
        }
        if (this.name != null) {
            taskDefinition.setName(this.name);
        }
        if (this.taskType != null) {
            taskDefinition.setTaskType(this.taskType);
        }
        return taskDefinition;
    }
}
