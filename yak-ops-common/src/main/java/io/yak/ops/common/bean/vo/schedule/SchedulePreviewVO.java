package io.yak.ops.common.bean.vo.schedule;

import java.time.Instant;
import java.util.List;

/**
 * Hypothetical future Cron times, not proof of a live scheduler registration.
 * @param after effective exclusive UTC lower bound
 * @param nextFireTimes immutable future UTC occurrences
 */
public record SchedulePreviewVO(Instant after, List<Instant> nextFireTimes) {
    public SchedulePreviewVO {
        nextFireTimes = List.copyOf(nextFireTimes);
    }
}
