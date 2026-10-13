package io.yak.ops.common.bean.dto.schedule;

import io.yak.ops.common.enums.schedule.ScheduleTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Creates a disabled schedule without accepting trusted runtime identity. */
@Data
public class CreateScheduleDTO {

    /** Root target type; only TASK is supported. */
    @NotNull
    private ScheduleTargetType targetType;
    /** Workspace-owned Task Definition identifier. */
    @NotBlank
    @Size(max = 64)
    private String targetId;
    /** Exact Quartz expression retained for visual or advanced editing. */
    @NotBlank
    @Size(max = 256)
    private String cronExpression;
    /** Explicit named time zone used by Cron evaluation. */
    @NotBlank
    @Size(max = 64)
    private String timeZone;
}
