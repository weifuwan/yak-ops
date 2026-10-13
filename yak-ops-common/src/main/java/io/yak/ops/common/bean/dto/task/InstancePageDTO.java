package io.yak.ops.common.bean.dto.task;

import io.yak.ops.common.bean.dto.common.PageQueryDTO;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Workspace-scoped Task Instance page filters. */
@Data
@EqualsAndHashCode(callSuper = true)
public class InstancePageDTO extends PageQueryDTO {
    /** Stable Definition identifier when inspecting one Task's execution history. */
    @Size(max = 64)
    private String definitionId;

    /** Business Instance lifecycle filter. */
    private TaskInstanceStatus status;

    /** Registered Task plugin identifier. */
    @Size(max = 64)
    private String taskType;
}
