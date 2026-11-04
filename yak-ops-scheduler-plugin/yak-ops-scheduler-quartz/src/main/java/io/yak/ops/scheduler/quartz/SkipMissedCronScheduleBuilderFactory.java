package io.yak.ops.scheduler.quartz;

import io.yak.ops.common.utils.DateUtils;
import io.yak.ops.dao.entity.Schedule;

import org.quartz.CronScheduleBuilder;

final class SkipMissedCronScheduleBuilderFactory implements CronScheduleBuilderFactory {

    @Override
    public CronScheduleBuilder createCronScheduleBuilder(Schedule schedule) {
        return CronScheduleBuilder.cronSchedule(schedule.getCrontab())
                .withMisfireHandlingInstructionDoNothing()
                .inTimeZone(DateUtils.getTimezone(schedule.getTimezoneId()));
    }
}
