package io.yak.ops.common.bean.dto.schedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Data;

/** Previews future times without creating a Trigger. */
@Data
public class SchedulePreviewDTO {

    /** Quartz expression to preview without saving a schedule. */
    @NotBlank
    @Size(max = 256)
    private String cronExpression;
    /** Named zone for evaluating the Cron expression. */
    @NotBlank
    @Size(max = 64)
    private String timeZone;
    /** Exclusive UTC lower bound; omitted requests use server UTC time. */
    private Instant after;
}
