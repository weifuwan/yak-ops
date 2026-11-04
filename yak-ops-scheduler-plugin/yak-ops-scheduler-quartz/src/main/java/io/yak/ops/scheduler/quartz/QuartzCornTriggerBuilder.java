package io.yak.ops.scheduler.quartz;

import io.yak.ops.common.utils.DateUtils;
import io.yak.ops.dao.entity.Schedule;

import java.util.Date;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobKey;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;

/**
 * QuartzCornTriggerBuilder used to build a {@link CronTrigger} instance.
 */
public class QuartzCornTriggerBuilder implements QuartzTriggerBuilder {

    private Integer projectId;

    private Schedule schedule;

    public static QuartzCornTriggerBuilder newBuilder() {
        return new QuartzCornTriggerBuilder();
    }

    public QuartzCornTriggerBuilder withProjectId(Integer projectId) {
        this.projectId = projectId;
        return this;
    }

    public QuartzCornTriggerBuilder withSchedule(Schedule schedule) {
        this.schedule = schedule;
        return this;
    }

    @Override
    public CronTrigger build() {

        if (projectId == null) {
            throw new IllegalArgumentException("projectId cannot be null");
        }
        if (schedule == null) {
            throw new IllegalArgumentException("schedule cannot be null");
        }

        /**
         * transform from server default timezone to schedule timezone
         * e.g. server default timezone is `UTC`
         * user set a schedule with startTime `2022-04-28 10:00:00`, timezone is `Asia/Shanghai`,
         * api skip to transform it and save into databases directly, startTime `2022-04-28 10:00:00`, timezone is `UTC`, which actually added 8 hours,
         * so when add job to quartz, it should recover by transform timezone
         */
        Date startDate = DateUtils.transformTimezoneDate(schedule.getStartTime(), schedule.getTimezoneId());
        Date endDate = DateUtils.transformTimezoneDate(schedule.getEndTime(), schedule.getTimezoneId());
        /**
         * If the start time is less than the current time, the start time is set to the current time.
         * We do this change to avoid misfires all triggers when update the scheduler.
         */
        Date now = new Date();
        if (startDate.before(now)) {
            startDate = now;
        }
        JobKey jobKey = QuartzJobKey.of(projectId, schedule.getId()).toJobKey();

        TriggerKey triggerKey = TriggerKey.triggerKey(jobKey.getName(), jobKey.getGroup());
        CronScheduleBuilder scheduleBuilder = CronScheduleBuilderFactory.getFactory(schedule.getMissedFirePolicy())
                .createCronScheduleBuilder(schedule);

        return TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .startAt(startDate)
                .endAt(endDate)
                .withSchedule(scheduleBuilder)
                .build();
    }

}
