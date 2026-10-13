package io.yak.ops.dao.entity.schedule;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.schedule.ScheduleTargetType;
import io.yak.ops.dao.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Durable desired schedule state; owns no Task execution or physical Quartz resources. */
@Getter
@Setter
@TableName("yak_ops_schedule")
public class ScheduleEntity extends BaseEntity {
    /** Workspace owning the target and schedule. */
    private String workspaceId;
    /** Supported root target category, independent of the Task plugin type. */
    private ScheduleTargetType targetType;
    /** Immutable target Definition identifier. */
    private String targetId;
    /** Exact persisted Quartz Cron expression. */
    private String cronExpression;
    /** Explicit named time zone used to interpret Cron. */
    private String timeZone;
    /** Desired registration state, false at creation. */
    private boolean enabled;
    /** Monotonically increasing revision for edits and trigger eligibility. */
    private Long revision;
    /** UTC enablement boundary, null until explicitly enabled. */
    private LocalDateTime enabledAt;
    /** Actor authorizing the most recent enablement, never a substitute for live authorization. */
    private String enabledBy;
    /** Monotonic UTC retention floor; occurrences below it cannot be readmitted. */
    private LocalDateTime retiredBefore;
    /** Logical deletion timestamp; null for a live configuration. */
    private LocalDateTime deletedAt;
}
