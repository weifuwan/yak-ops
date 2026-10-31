package io.yak.ops.common.bean.dto.task;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Publishes one saved parameter version and the diagnostics setting at the expected edit revision. */
@Data
public class PublishDefinitionDTO {
    /** Immutable parameter version selected for future executions. */
    @NotNull
    @Min(1)
    private Integer version;

    /** Task revision whose saved diagnostics setting is included in this publication. */
    @NotNull
    @Min(1)
    private Long expectedRevision;

    /** Caller-stable key reused only when retrying the same publication request. */
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{8,128}")
    private String requestId;
}
