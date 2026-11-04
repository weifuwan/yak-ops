package io.yak.ops.scheduler.quartz;

import io.yak.ops.common.utils.DateUtils;
import io.yak.ops.dao.entity.Schedule;

import org.quartz.CronScheduleBuilder;

final class FireOnceNowCronScheduleBuilderFactory implements CronScheduleBuilderFactory {

    @Override
    public CronScheduleBuilder createCronScheduleBuilder(Schedule schedule) {
        return CronScheduleBuilder.cronSchedule(schedule.getCrontab())
                .withMisfireHandlingInstructionFireAndProceed()
                .inTimeZone(DateUtils.getTimezone(schedule.getTimezoneId()));
    }
}
