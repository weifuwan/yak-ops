package io.yak.ops.common.bean.dto.schedule;

import io.yak.ops.common.bean.dto.common.PageQueryDTO;
import io.yak.ops.common.enums.schedule.ScheduleTargetType;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Bounded Workspace schedule filters with a stable server-owned sort order. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScheduleQueryDTO extends PageQueryDTO {
    /** Optional exact root target type. */
    private ScheduleTargetType targetType;
    /** Optional exact target identifier. */
    @Size(max = 64)
    private String targetId;
    /** Optional desired enablement filter, not registration health. */
    private Boolean enabled;
}
