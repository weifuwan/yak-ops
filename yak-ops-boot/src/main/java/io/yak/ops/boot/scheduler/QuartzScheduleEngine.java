package io.yak.ops.boot.scheduler;

import io.yak.ops.business.datasync.scheduler.DataSyncScheduleDefinition;
import io.yak.ops.business.datasync.scheduler.ScheduleEngine;
import io.yak.ops.business.datasync.scheduler.ScheduleEngineException;
import io.yak.ops.common.util.StringUtils;
import jakarta.annotation.Resource;
import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Component;

/**
 * 使用 Quartz 实现 Data Sync 的时钟边界，只保存稳定资源 ID 和 Cron Trigger，不承载 Data Sync 业务状态。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Component
public class QuartzScheduleEngine implements ScheduleEngine {

    static final String QUARTZ_GROUP = "yak-ops-data-sync";
    static final String JOB_PREFIX = "job-";
    static final String TRIGGER_PREFIX = "trigger-";
    static final String DATA_SCHEDULE_ID = "scheduleId";
    static final String DATA_WORKSPACE_ID = "workspaceId";
    static final String DATA_TASK_ID = "taskId";

    @Resource
    private Scheduler scheduler;

    @Override
    public void validate(DataSyncScheduleDefinition definition) {
        try {
            buildTrigger(definition);
        } catch (RuntimeException exception) {
            throw new ScheduleEngineException(
                    "Quartz schedule definition is invalid: " + definition.scheduleId(), exception);
        }
    }

    @Override
    public void schedule(DataSyncScheduleDefinition definition) {
        try {
            scheduler.scheduleJob(buildJob(definition), buildTrigger(definition));
        } catch (SchedulerException | RuntimeException exception) {
            throw new ScheduleEngineException(
                    "Quartz schedule registration failed: " + definition.scheduleId(), exception);
        }
    }

    @Override
    public void reschedule(DataSyncScheduleDefinition definition) {
        JobKey jobKey = jobKey(definition.scheduleId());
        TriggerKey triggerKey = triggerKey(definition.scheduleId());
        try {
            if (!scheduler.checkExists(jobKey) || !scheduler.checkExists(triggerKey)) {
                throw new ScheduleEngineException("Schedule does not exist: " + definition.scheduleId());
            }
            scheduler.addJob(buildJob(definition), true, true);
            Date nextFireTime = scheduler.rescheduleJob(triggerKey, buildTrigger(definition));
            if (nextFireTime == null)
                throw new ScheduleEngineException("Schedule does not exist: " + definition.scheduleId());
        } catch (ScheduleEngineException exception) {
            throw exception;
        } catch (SchedulerException | RuntimeException exception) {
            throw new ScheduleEngineException("Quartz schedule update failed: " + definition.scheduleId(), exception);
        }
    }

    @Override
    public void unschedule(String scheduleId) {
        requireScheduleId(scheduleId);
        try {
            scheduler.deleteJob(jobKey(scheduleId));
        } catch (SchedulerException exception) {
            throw new ScheduleEngineException("Quartz schedule removal failed: " + scheduleId, exception);
        }
    }

    @Override
    public Optional<Instant> queryNextFireTime(String scheduleId) {
        requireScheduleId(scheduleId);
        try {
            Trigger trigger = scheduler.getTrigger(triggerKey(scheduleId));
            if (trigger == null || trigger.getNextFireTime() == null) return Optional.empty();
            return Optional.of(trigger.getNextFireTime().toInstant());
        } catch (SchedulerException exception) {
            throw new ScheduleEngineException("Quartz next fire time query failed: " + scheduleId, exception);
        }
    }

    @Override
    public List<Instant> previewNextFireTimes(String cronExpression, ZoneId timeZone, int count) {
        if (count <= 0) throw new IllegalArgumentException("count must be greater than 0");
        try {
            CronExpression expression = new CronExpression(cronExpression);
            expression.setTimeZone(TimeZone.getTimeZone(timeZone));

            List<Instant> result = new ArrayList<>(count);
            Date cursor = Date.from(Instant.now());
            for (int index = 0; index < count; index++) {
                Date next = expression.getNextValidTimeAfter(cursor);
                if (next == null) break;
                result.add(next.toInstant());
                cursor = next;
            }
            return result;
        } catch (ParseException | RuntimeException exception) {
            throw new ScheduleEngineException("Quartz schedule preview failed", exception);
        }
    }

    private JobDetail buildJob(DataSyncScheduleDefinition definition) {
        return JobBuilder.newJob(QuartzDataSyncScheduleJob.class)
                .withIdentity(jobKey(definition.scheduleId()))
                .usingJobData(DATA_SCHEDULE_ID, definition.scheduleId())
                .usingJobData(DATA_WORKSPACE_ID, definition.workspaceId())
                .usingJobData(DATA_TASK_ID, definition.taskId())
                .build();
    }

    private Trigger buildTrigger(DataSyncScheduleDefinition definition) {
        CronScheduleBuilder cron = CronScheduleBuilder.cronSchedule(definition.cronExpression())
                .inTimeZone(TimeZone.getTimeZone(definition.timeZone()))
                .withMisfireHandlingInstructionDoNothing();
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerKey(definition.scheduleId()))
                .forJob(jobKey(definition.scheduleId()))
                .withSchedule(cron)
                .build();
    }

    private JobKey jobKey(String scheduleId) {
        return new JobKey(JOB_PREFIX + requireScheduleId(scheduleId), QUARTZ_GROUP);
    }

    private TriggerKey triggerKey(String scheduleId) {
        return new TriggerKey(TRIGGER_PREFIX + requireScheduleId(scheduleId), QUARTZ_GROUP);
    }

    private String requireScheduleId(String scheduleId) {
        if (StringUtils.isBlank(scheduleId)) throw new IllegalArgumentException("scheduleId must not be blank");
        return scheduleId;
    }
}
