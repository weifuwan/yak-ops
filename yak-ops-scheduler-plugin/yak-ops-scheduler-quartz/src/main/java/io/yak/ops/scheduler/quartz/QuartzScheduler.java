package io.yak.ops.scheduler.quartz;

import io.yak.ops.dao.entity.Schedule;
import io.yak.ops.scheduler.api.SchedulerApi;
import io.yak.ops.scheduler.api.SchedulerException;
import io.yak.ops.scheduler.quartz.exception.QuartzSchedulerExceptionEnum;

import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;

import com.google.common.collect.Sets;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QuartzScheduler implements SchedulerApi {

    private final Scheduler scheduler;

    public QuartzScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void start() throws SchedulerException {
        try {
            scheduler.start();
        } catch (Exception e) {
            throw new SchedulerException(QuartzSchedulerExceptionEnum.QUARTZ_SCHEDULER_START_ERROR, e);
        }
    }

    @Override
    public void insertOrUpdateScheduleTask(int projectId, Schedule schedule) throws SchedulerException {
        try {
            CronTrigger cornTrigger = QuartzCornTriggerBuilder.newBuilder()
                    .withProjectId(projectId)
                    .withSchedule(schedule)
                    .build();
            JobDetail jobDetail = QuartzJobDetailBuilder.newBuilder()
                    .withProjectId(projectId)
                    .withSchedule(schedule.getId())
                    .build();
            scheduler.scheduleJob(jobDetail, Sets.newHashSet(cornTrigger), true);
            log.info("Success scheduleJob: {} with trigger: {} at quartz", jobDetail, cornTrigger);
        } catch (Exception e) {
            log.error("Failed to add scheduler task, projectId: {}, scheduler: {}", projectId, schedule, e);
            throw new SchedulerException(QuartzSchedulerExceptionEnum.QUARTZ_UPSERT_JOB_ERROR, e);
        }
    }

    @Override
    public void deleteScheduleTask(int projectId, int scheduleId) throws SchedulerException {
        JobKey jobKey = QuartzJobKey.of(projectId, scheduleId).toJobKey();
        try {
            if (scheduler.checkExists(jobKey)) {
                log.info("Try to delete scheduler task, projectId: {}, schedulerId: {}", projectId, scheduleId);
                scheduler.deleteJob(jobKey);
            }
        } catch (Exception e) {
            log.error("Failed to delete scheduler task, projectId: {}, schedulerId: {}", projectId, scheduleId, e);
            throw new SchedulerException(QuartzSchedulerExceptionEnum.QUARTZ_DELETE_JOB_ERROR, e);
        }
    }

    @Override
    public void close() {
        // nothing to do
        try {
            scheduler.shutdown();
        } catch (Exception e) {
            throw new SchedulerException(QuartzSchedulerExceptionEnum.QUARTZ_SCHEDULER_SHOWDOWN_ERROR, e);
        }
    }
}
