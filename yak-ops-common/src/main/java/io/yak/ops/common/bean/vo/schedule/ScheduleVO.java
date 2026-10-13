package io.yak.ops.common.bean.vo.schedule;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Schedule details with live target publication and plugin eligibility. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScheduleVO extends ScheduleSummaryVO {
    /** Whether the target is published and its parsed plugin parameters permit scheduling. */
    private boolean schedulable;
    /** Structured blocking reason, null when the target is eligible. */
    private String eligibilityReason;
}
