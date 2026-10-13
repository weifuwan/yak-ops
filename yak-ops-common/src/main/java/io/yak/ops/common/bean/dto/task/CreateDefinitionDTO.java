package io.yak.ops.common.bean.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Generic Task Definition creation request. */
@Data
public class CreateDefinitionDTO {
    /** Display name unique within the current Workspace. */
    @NotBlank
    @Size(max = 128)
    private String name;

    /** Registered plugin type used to parse and validate parameters. */
    @NotBlank
    @Size(max = 64)
    private String taskType;

    /** Plugin-specific definition JSON; credentials must not be embedded. */
    @NotBlank
    private String parameters;

    /** Detailed diagnostics for future submissions, disabled by default. */
    private Boolean debug;

    /** Optional human-readable description. */
    @Size(max = 500)
    private String remark;
}
