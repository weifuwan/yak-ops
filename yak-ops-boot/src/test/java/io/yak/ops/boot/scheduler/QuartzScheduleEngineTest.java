package io.yak.ops.boot.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.datasync.scheduler.DataSyncScheduleDefinition;
import io.yak.ops.business.datasync.scheduler.ScheduleEngineException;
import java.lang.reflect.Field;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.TriggerKey;

class QuartzScheduleEngineTest {

    private Scheduler scheduler;
    private QuartzScheduleEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", "QuartzScheduleEngineTest-" + UUID.randomUUID());
        properties.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        properties.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        properties.setProperty("org.quartz.threadPool.threadCount", "1");
        properties.setProperty("org.quartz.threadPool.threadPriority", "5");
        properties.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");

        scheduler = new StdSchedulerFactory(properties).getScheduler();
        engine = new QuartzScheduleEngine();
        injectScheduler(engine, scheduler);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (scheduler != null) scheduler.shutdown(true);
    }

    @Test
    void shouldRegisterCronWithExplicitTimezoneAndStableIdsOnly() throws Exception {
        DataSyncScheduleDefinition definition = new DataSyncScheduleDefinition(
                "schedule-1", "workspace-1", "task-1", "0 0 2 * * ?", ZoneId.of("Asia/Shanghai"));

        engine.schedule(definition);

        JobDetail job = scheduler.getJobDetail(jobKey("schedule-1"));
        CronTrigger trigger = (CronTrigger) scheduler.getTrigger(triggerKey("schedule-1"));

        assertEquals("schedule-1", job.getJobDataMap().getString(QuartzScheduleEngine.DATA_SCHEDULE_ID));
        assertEquals("workspace-1", job.getJobDataMap().getString(QuartzScheduleEngine.DATA_WORKSPACE_ID));
        assertEquals("task-1", job.getJobDataMap().getString(QuartzScheduleEngine.DATA_TASK_ID));
        assertEquals(3, job.getJobDataMap().size());
        assertEquals("0 0 2 * * ?", trigger.getCronExpression());
        assertEquals("Asia/Shanghai", trigger.getTimeZone().getID());
        assertEquals(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING, trigger.getMisfireInstruction());
        assertTrue(engine.queryNextFireTime("schedule-1").isPresent());
    }

    @Test
    void shouldPreviewNextFiveFireTimesWithExplicitTimezone() {
        ZoneId timeZone = ZoneId.of("Asia/Shanghai");

        List<java.time.Instant> preview = engine.previewNextFireTimes("0 0 2 * * ?", timeZone, 5);

        assertEquals(5, preview.size());
        assertTrue(preview.stream()
                .map(instant -> ZonedDateTime.ofInstant(instant, timeZone))
                .allMatch(time -> time.getHour() == 2 && time.getMinute() == 0 && time.getSecond() == 0));
        for (int index = 1; index < preview.size(); index++) {
            assertTrue(preview.get(index).isAfter(preview.get(index - 1)));
        }
    }

    @Test
    void shouldRejectInvalidCronDuringValidation() {
        DataSyncScheduleDefinition definition = new DataSyncScheduleDefinition(
                "schedule-1", "workspace-1", "task-1", "invalid-cron", ZoneId.of("Asia/Shanghai"));

        assertThrows(ScheduleEngineException.class, () -> engine.validate(definition));
    }

    @Test
    void shouldRescheduleAndUnscheduleIdempotently() throws Exception {
        engine.schedule(new DataSyncScheduleDefinition(
                "schedule-1", "workspace-1", "task-1", "0 0 2 * * ?", ZoneId.of("Asia/Shanghai")));

        engine.reschedule(new DataSyncScheduleDefinition(
                "schedule-1", "workspace-1", "task-1", "0 30 3 * * ?", ZoneId.of("Asia/Shanghai")));

        CronTrigger trigger = (CronTrigger) scheduler.getTrigger(triggerKey("schedule-1"));
        assertEquals("0 30 3 * * ?", trigger.getCronExpression());

        engine.unschedule("schedule-1");
        engine.unschedule("schedule-1");

        assertFalse(scheduler.checkExists(jobKey("schedule-1")));
        assertFalse(scheduler.checkExists(triggerKey("schedule-1")));
        assertTrue(engine.queryNextFireTime("schedule-1").isEmpty());
    }

    private JobKey jobKey(String scheduleId) {
        return new JobKey(QuartzScheduleEngine.JOB_PREFIX + scheduleId, QuartzScheduleEngine.QUARTZ_GROUP);
    }

    private TriggerKey triggerKey(String scheduleId) {
        return new TriggerKey(QuartzScheduleEngine.TRIGGER_PREFIX + scheduleId, QuartzScheduleEngine.QUARTZ_GROUP);
    }

    private void injectScheduler(QuartzScheduleEngine target, Scheduler value) throws Exception {
        Field field = QuartzScheduleEngine.class.getDeclaredField("scheduler");
        field.setAccessible(true);
        field.set(target, value);
    }
}
