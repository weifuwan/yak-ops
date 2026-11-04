package io.yak.ops.scheduler.quartz;

import io.yak.ops.common.enums.ScheduleMissedFirePolicy;
import io.yak.ops.dao.entity.Schedule;

import org.quartz.CronScheduleBuilder;

interface CronScheduleBuilderFactory {

    CronScheduleBuilder createCronScheduleBuilder(Schedule schedule);

    static CronScheduleBuilderFactory getFactory(ScheduleMissedFirePolicy missedFirePolicy) {
        ScheduleMissedFirePolicy effectivePolicy = missedFirePolicy == null
                ? ScheduleMissedFirePolicy.FIRE_ALL_MISSED
                : missedFirePolicy;
        switch (effectivePolicy) {
            case SKIP_MISSED:
                return new SkipMissedCronScheduleBuilderFactory();
            case FIRE_ONCE_NOW:
                return new FireOnceNowCronScheduleBuilderFactory();
            case FIRE_ALL_MISSED:
                return new FireAllMissedCronScheduleBuilderFactory();
            default:
                throw new IllegalArgumentException("Unsupported schedule missed fire policy: " + missedFirePolicy);
        }
    }
}
