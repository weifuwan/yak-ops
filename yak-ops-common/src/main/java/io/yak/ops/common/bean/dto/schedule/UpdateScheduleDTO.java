package io.yak.ops.common.bean.dto.schedule;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Updates time rules only while the schedule is disabled. */
@Data
public class UpdateScheduleDTO {

    /** Expected current revision; target identity and enablement are not editable here. */
    @NotNull
    @Min(1)
    private Long revision;
    /** Exact replacement Quartz expression. */
    @NotBlank
    @Size(max = 256)
    private String cronExpression;
    /** Explicit replacement named time zone. */
    @NotBlank
    @Size(max = 64)
    private String timeZone;
}
