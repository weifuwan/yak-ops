package io.yak.ops.business.task.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.yak.ops.business.task.submission.impl.SubmissionServiceImpl;
import io.yak.ops.business.task.submission.impl.TaskSubmissionTransactionServiceImpl;
import io.yak.ops.common.enums.schedule.ScheduleFireOutcome;
import io.yak.ops.common.enums.schedule.ScheduleFireReason;
import io.yak.ops.common.enums.schedule.ScheduleTargetType;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.TaskGroupQueueEntity;
import io.yak.ops.dao.repository.schedule.ScheduleFireRepository;
import io.yak.ops.dao.repository.schedule.ScheduleRepository;
import io.yak.ops.dao.repository.schedule.impl.ScheduleFireRepositoryImpl;
import io.yak.ops.dao.repository.schedule.impl.ScheduleRepositoryImpl;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import io.yak.ops.dao.repository.task.TaskGroupQueueRepository;
import io.yak.ops.dao.repository.task.impl.DefinitionRepositoryImpl;
import io.yak.ops.dao.repository.task.impl.DefinitionVersionRepositoryImpl;
import io.yak.ops.dao.repository.task.impl.InstanceRepositoryImpl;
import io.yak.ops.dao.repository.task.impl.TaskGroupQueueRepositoryImpl;
import io.yak.ops.plugin.scheduler.api.CronService;
import io.yak.ops.plugin.scheduler.api.ScheduleRegistration;
import io.yak.ops.plugin.scheduler.api.ScheduleTrigger;
import io.yak.ops.plugin.scheduler.quartz.QuartzCronService;
import io.yak.ops.plugin.scheduler.quartz.QuartzScheduler;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.plugin.task.api.TaskPluginManager;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.task.api.validation.TaskValidationContextFactory;
import io.yak.ops.testutils.MySqlDatabase;
import io.yak.ops.workspace.WorkspaceService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.apache.dolphinscheduler.plugin.registry.jdbc.JdbcRegistryProperties;
import org.apache.dolphinscheduler.plugin.registry.jdbc.repository.JdbcRegistryClientRepository;
import org.apache.dolphinscheduler.plugin.registry.jdbc.repository.JdbcRegistryDataChangeEventRepository;
import org.apache.dolphinscheduler.plugin.registry.jdbc.repository.JdbcRegistryDataRepository;
import org.apache.dolphinscheduler.plugin.registry.jdbc.repository.JdbcRegistryLockRepository;
import org.apache.dolphinscheduler.plugin.registry.jdbc.server.JdbcRegistryServer;
import org.apache.dolphinscheduler.registry.api.Registry;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Real MySQL savepoint, race, commit-uncertainty, retention, and Quartz JDBC JobStore contracts. */
@Tag("business")
@Timeout(180)
class ScheduleAdmissionIT {
    private static final Instant BASE = Instant.parse("2026-10-13T00:00:00Z");
    private static final AtomicReference<Instant> NOW = new AtomicReference<>(BASE);
    private static final AtomicBoolean LOSE_COMMIT_RESPONSE = new AtomicBoolean();
    private static final WorkspaceService AUTHORITY = mock(WorkspaceService.class);
    private static final TestPlugin PLUGIN = new TestPlugin();
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbc;
    private static ScheduleAdmissionController admissionController;
    private static SubmissionService submissions;
    private static DataSource dataSource;
    private static final Registry REGISTRY = mock(Registry.class);

    @RegisterExtension
    static final MySqlDatabase MYSQL = new MySqlDatabase("mysql-admission", false, false);

    @BeforeAll
    static void startDatabaseBoundary() {
        String url = MYSQL.metadata().url();
        DriverManagerDataSource source =
                new DriverManagerDataSource(
                        url, MYSQL.metadata().username(), MYSQL.metadata().password()) {
                    @Override
                    public Connection getConnection() throws SQLException {
                        Connection connection = super.getConnection();
                        return (Connection) Proxy.newProxyInstance(
                                Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) -> {
                                    try {
                                        Object result = method.invoke(connection, args);
                                        if (method.getName().equals("commit")
                                                && LOSE_COMMIT_RESPONSE.compareAndSet(true, false)) {
                                            throw new SQLException("Injected loss of commit acknowledgement");
                                        }
                                        return result;
                                    } catch (InvocationTargetException failure) {
                                        throw failure.getCause();
                                    }
                                });
                    }
                };
        dataSource = source;
        jdbc = new JdbcTemplate(source);
        Flyway.configure()
                .dataSource(source)
                .locations("classpath:db/migration/yak-ops")
                .load()
                .migrate();
        insertDefinition();
        jdbc.update(
                "INSERT INTO yak_ops_task_instance (id,workspace_id,definition_id,definition_version,task_type,trigger_type,request_id,request_fingerprint,resource_revision,status,state_version,create_time,update_time,create_by,update_by) VALUES ('historical','ws','task',1,'SCHEDULE_TEST','MANUAL','historical-key','MANUAL:task','v1','FINISHED',0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),'actor','actor')");
        assertEquals(1, count("yak_ops_task_instance"));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM yak_ops_task_instance WHERE id='historical' AND schedule_id IS NULL AND scheduled_time IS NULL",
                        Integer.class));
        context = new AnnotationConfigApplicationContext();
        context.register(
                Beans.class,
                DefinitionRepositoryImpl.class,
                DefinitionVersionRepositoryImpl.class,
                InstanceRepositoryImpl.class,
                TaskGroupQueueRepositoryImpl.class,
                ScheduleRepositoryImpl.class,
                ScheduleFireRepositoryImpl.class,
                SubmissionServiceImpl.class,
                TaskSubmissionTransactionServiceImpl.class,
                JdbcRegistryClientRepository.class,
                JdbcRegistryDataRepository.class,
                JdbcRegistryDataChangeEventRepository.class,
                JdbcRegistryLockRepository.class);
        context.refresh();
        admissionController = context.getBean(ScheduleAdmissionController.class);
        submissions = context.getBean(SubmissionService.class);
    }

    @AfterAll
    static void closeBoundary() {
        if (admissionController != null) {
            admissionController.stop();
            admissionController.awaitQuiescence();
        }
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void resetRows() {
        admissionController.stop();
        admissionController.awaitQuiescence();
        LOSE_COMMIT_RESPONSE.set(false);
        reset(REGISTRY);
        when(REGISTRY.isConnected()).thenReturn(true);
        when(REGISTRY.get("/yak-ops/masters/embedded")).thenReturn("test-owner");
        for (String table : List.of(
                "yak_ops_task_group_queue",
                "yak_ops_task_group",
                "yak_ops_task_instance",
                "yak_ops_schedule_fire",
                "yak_ops_schedule",
                "yak_ops_task_definition_version",
                "yak_ops_task_definition")) {
            jdbc.update("DELETE FROM " + table);
        }
        for (String table : List.of(
                "FIRED_TRIGGERS",
                "SIMPLE_TRIGGERS",
                "SIMPROP_TRIGGERS",
                "CRON_TRIGGERS",
                "BLOB_TRIGGERS",
                "TRIGGERS",
                "JOB_DETAILS",
                "CALENDARS",
                "PAUSED_TRIGGER_GRPS",
                "SCHEDULER_STATE",
                "LOCKS")) {
            jdbc.update("DELETE FROM YAK_QRTZ_" + table);
        }
        reset(AUTHORITY);
        when(AUTHORITY.isActiveMember("ws", "actor")).thenReturn(true);
        PLUGIN.reset();
        insertDefinition();
        jdbc.update(
                "INSERT INTO yak_ops_schedule (id,workspace_id,target_type,target_id,cron_expression,time_zone,enabled,revision,enabled_at,enabled_by,create_time,update_time,create_by,update_by) VALUES ('schedule','ws','TASK','task','0 * * * * ?','UTC',1,2,'2026-10-13 00:00:00','actor',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),'actor','actor')");
        NOW.set(BASE);
        admissionController.prepare("test-owner", () -> {});
        admissionController.open();
        NOW.set(BASE.plusSeconds(60));
    }

    @Test
    void concurrentNotificationsCreateOneInstanceAndReplayTheSameVersion() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch ready = new CountDownLatch(1);
            List<java.util.concurrent.Future<ScheduleFireResult>> results = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                results.add(executor.submit(() -> {
                    ready.await();
                    return submissions.submitScheduled(trigger());
                }));
            }
            ready.countDown();
            int admitted = 0;
            String instance = null;
            for (var future : results) {
                ScheduleFireResult result = future.get(20, TimeUnit.SECONDS);
                assertEquals(ScheduleFireOutcome.ADMITTED, result.outcome());
                if (!result.replayed()) {
                    admitted++;
                }
                if (instance == null) {
                    instance = result.instanceId();
                }
                assertEquals(instance, result.instanceId());
            }
            assertEquals(1, admitted);
            assertEquals(1, count("yak_ops_task_instance"));
            assertEquals(1, count("yak_ops_schedule_fire"));
            saveNextDraft(true);
            jdbc.update("UPDATE yak_ops_task_definition SET published_version=2,published_debug=1 WHERE id='task'");
            assertTrue(submissions.submitScheduled(trigger()).replayed());
            assertEquals(1, jdbc.queryForObject("SELECT definition_version FROM yak_ops_task_instance", Integer.class));
        }
    }

    @Test
    void partialClaimConflictRollsBackAllWritesAndSkipNeverReopens() {
        PLUGIN.resources = List.of(new ExecutionResource("TEST", "a-free"), new ExecutionResource("TEST", "z-busy"));
        occupy("z-busy");
        ScheduleFireResult first = submissions.submitScheduled(trigger());
        assertEquals(ScheduleFireOutcome.SKIPPED, first.outcome());
        assertEquals(ScheduleFireReason.RESOURCE_BUSY, first.reason());
        assertEquals(0, count("yak_ops_task_instance"));
        assertEquals(1, count("yak_ops_task_group_queue"));
        assertEquals(1, count("yak_ops_schedule_fire"));
        jdbc.update("DELETE FROM yak_ops_task_group_queue");
        jdbc.update("UPDATE yak_ops_task_group SET use_size = 0");
        ScheduleFireResult replay = submissions.submitScheduled(trigger());
        assertTrue(replay.replayed());
        assertEquals(ScheduleFireOutcome.SKIPPED, replay.outcome());
        assertEquals(0, count("yak_ops_task_instance"));
    }

    @Test
    void disableFirstPreventsAdmissionAndOldRevisionStaysRejected() {
        stop(false);
        ScheduleFireResult result = submissions.submitScheduled(trigger());
        assertEquals(ScheduleFireReason.STALE_SCHEDULE, result.reason());
        assertEquals(0, count("yak_ops_task_instance"));
        jdbc.update("UPDATE yak_ops_schedule SET enabled=1,revision=4 WHERE id='schedule'");
        assertTrue(submissions.submitScheduled(trigger()).replayed());
        assertEquals(0, count("yak_ops_task_instance"));
    }

    @Test
    void admissionFirstCommitsBeforeConcurrentDisableWithoutCancellingInstance() throws Exception {
        PLUGIN.blockCall = 2;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var fire = executor.submit(() -> submissions.submitScheduled(trigger()));
            assertTrue(PLUGIN.entered.await(10, TimeUnit.SECONDS));
            CountDownLatch stopping = new CountDownLatch(1);
            var stop = executor.submit(() -> {
                stopping.countDown();
                stop(false);
            });
            try {
                assertTrue(stopping.await(5, TimeUnit.SECONDS));
                assertFalse(stop.isDone());
            } finally {
                PLUGIN.release.countDown();
            }
            assertEquals(
                    ScheduleFireOutcome.ADMITTED, fire.get(15, TimeUnit.SECONDS).outcome());
            stop.get(15, TimeUnit.SECONDS);
            assertEquals("SUBMITTED", jdbc.queryForObject("SELECT status FROM yak_ops_task_instance", String.class));
            assertEquals(0, jdbc.queryForObject("SELECT enabled FROM yak_ops_schedule", Integer.class));
            assertEquals(1, count("yak_ops_task_group_queue"));
        }
    }

    @Test
    void unpublishUsesTheSameOrderAndPreservesCommittedInstances() throws Exception {
        PLUGIN.blockCall = 2;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var fire = executor.submit(() -> submissions.submitScheduled(trigger()));
            assertTrue(PLUGIN.entered.await(10, TimeUnit.SECONDS));
            var unpublished = executor.submit(() -> stop(true));
            PLUGIN.release.countDown();
            assertEquals(
                    ScheduleFireOutcome.ADMITTED, fire.get(15, TimeUnit.SECONDS).outcome());
            unpublished.get(15, TimeUnit.SECONDS);
            assertEquals(1, count("yak_ops_task_instance"));
            assertEquals(0, jdbc.queryForObject("SELECT enabled FROM yak_ops_schedule", Integer.class));
            assertTrue(submissions.submitScheduled(trigger()).replayed());
        }
    }

    @Test
    void publicationChangedAfterPreparationDoesNotMixVersions() throws Exception {
        PLUGIN.blockCall = 1;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var fire = executor.submit(() -> submissions.submitScheduled(trigger()));
            try {
                assertTrue(PLUGIN.entered.await(10, TimeUnit.SECONDS));
                saveNextDraft(false);
                jdbc.update("UPDATE yak_ops_task_definition SET published_version=2 WHERE id='task'");
            } finally {
                PLUGIN.release.countDown();
            }
            assertEquals(
                    ScheduleFireReason.DEFINITION_CHANGED,
                    fire.get(15, TimeUnit.SECONDS).reason());
            assertEquals(0, count("yak_ops_task_instance"));
        }
    }

    @Test
    void draftSaveAfterPreparationDoesNotInterruptScheduledAdmission() throws Exception {
        PLUGIN.blockCall = 1;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var fire = executor.submit(() -> submissions.submitScheduled(trigger()));
            try {
                assertTrue(PLUGIN.entered.await(10, TimeUnit.SECONDS));
                saveNextDraft(true);
            } finally {
                PLUGIN.release.countDown();
            }
            assertEquals(ScheduleFireOutcome.ADMITTED, fire.get(15, TimeUnit.SECONDS).outcome());
            assertEquals(1, jdbc.queryForObject("SELECT definition_version FROM yak_ops_task_instance", Integer.class));
            assertEquals(false, jdbc.queryForObject("SELECT debug FROM yak_ops_task_instance", Boolean.class));
            assertEquals(2L, jdbc.queryForObject("SELECT revision FROM yak_ops_schedule", Long.class));
        }
    }

    @Test
    void debugOnlyPublicationAfterPreparationRejectsTheStaleScheduledAdmission() throws Exception {
        PLUGIN.blockCall = 1;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var fire = executor.submit(() -> submissions.submitScheduled(trigger()));
            try {
                assertTrue(PLUGIN.entered.await(10, TimeUnit.SECONDS));
                jdbc.update("UPDATE yak_ops_task_definition SET debug=1,published_debug=1,revision=revision+1 WHERE id='task'");
            } finally {
                PLUGIN.release.countDown();
            }
            assertEquals(ScheduleFireReason.DEFINITION_CHANGED, fire.get(15, TimeUnit.SECONDS).reason());
            assertEquals(0, count("yak_ops_task_instance"));
            assertEquals(1, count("yak_ops_schedule_fire"));
        }
    }

    @Test
    void changedBindingAndRevokedAuthorityAreNotResourceSkips() {
        PLUGIN.changeBinding = true;
        assertEquals(
                ScheduleFireReason.BINDING_CHANGED,
                submissions.submitScheduled(trigger()).reason());
        NOW.set(BASE.plusSeconds(120));
        when(AUTHORITY.isActiveMember("ws", "actor")).thenReturn(false);
        int before = PLUGIN.plans.get();
        ScheduleFireResult rejected = submissions.submitScheduled(triggerAt(BASE.plusSeconds(120)));
        assertEquals(ScheduleFireReason.UNAUTHORIZED, rejected.reason());
        assertEquals(before, PLUGIN.plans.get());
        assertEquals(0, count("yak_ops_task_instance"));
    }

    @Test
    void oldOwnerTimesAreRejectedEvenInsideTheMisfireThreshold() {
        ScheduleTrigger old =
                new ScheduleTrigger("ws", "schedule", 2, BASE, BASE.plusSeconds(1), BASE.minusSeconds(60));
        NOW.set(BASE.plusSeconds(10));
        assertEquals(
                ScheduleFireReason.MISSED_WINDOW,
                submissions.submitScheduled(old).reason());
        assertEquals(0, count("yak_ops_task_instance"));
    }

    @Test
    void maximumLatenessIsInclusiveAndLaterUnadmittedFiresAreRejected() {
        NOW.set(BASE.plusSeconds(120));
        assertEquals(
                ScheduleFireOutcome.ADMITTED,
                submissions.submitScheduled(trigger()).outcome());
        NOW.set(BASE.plusSeconds(241));
        ScheduleFireResult late = submissions.submitScheduled(triggerAt(BASE.plusSeconds(180)));
        assertEquals(ScheduleFireReason.MISSED_WINDOW, late.reason());
        assertEquals(1, count("yak_ops_task_instance"));
    }

    @Test
    void purgedSkipHistoryCannotAuthorizeAnotherExecution() {
        occupy("target");
        assertEquals(
                ScheduleFireOutcome.SKIPPED,
                submissions.submitScheduled(trigger()).outcome());
        jdbc.update("DELETE FROM yak_ops_task_group_queue");
        jdbc.update("UPDATE yak_ops_task_group SET use_size = 0");
        context.getBean(ScheduleFireRepository.class)
                .retire("ws", "schedule", LocalDateTime.ofInstant(BASE.plusSeconds(61), ZoneOffset.UTC));
        assertEquals(0, count("yak_ops_schedule_fire"));
        assertEquals(
                ScheduleFireReason.MISSED_WINDOW,
                submissions.submitScheduled(trigger()).reason());
        assertEquals(0, count("yak_ops_task_instance"));
    }

    @Test
    void lostCommitResponseReplaysCommittedEvidenceWithoutAReplacementInstance() {
        LOSE_COMMIT_RESPONSE.set(true);
        assertThrows(RuntimeException.class, () -> submissions.submitScheduled(trigger()));
        assertFalse(LOSE_COMMIT_RESPONSE.get());
        assertEquals(1, count("yak_ops_task_instance"));
        assertEquals(1, count("yak_ops_schedule_fire"));
        stop(false);
        ScheduleFireResult replay = submissions.submitScheduled(trigger());
        assertTrue(replay.replayed());
        assertEquals(ScheduleFireOutcome.ADMITTED, replay.outcome());
        assertEquals(1, count("yak_ops_task_instance"));
    }

    @Test
    void databaseOwnerChangeAndMissingPermitFailClosed() {
        assertThrows(
                IllegalStateException.class,
                () -> context.getBean(TaskSubmissionTransactionService.class).admitScheduled(trigger(), null, null));
        when(REGISTRY.get("/yak-ops/masters/embedded")).thenReturn("other-owner");
        assertThrows(IllegalStateException.class, () -> submissions.submitScheduled(trigger()));
        assertFalse(admissionController.isOpen());
        assertEquals(0, count("yak_ops_task_instance"));
        assertEquals(0, count("yak_ops_schedule_fire"));
    }

    @Test
    void jdbcJobStorePreservesRegistrationAndDeliversImmutableTriggerMetadata() throws Exception {
        LinkedBlockingQueue<ScheduleTrigger> notifications = new LinkedBlockingQueue<>();
        ScheduleRegistration registration =
                new ScheduleRegistration("ws", "engine-schedule", 7, "* * * * * ?", "UTC", Instant.now());
        try (QuartzScheduler scheduler = new QuartzScheduler(dataSource, notifications::add, Duration.ofSeconds(60))) {
            assertThrows(IllegalStateException.class, () -> scheduler.registration("ws", "engine-schedule"));
            scheduler.initialize();
            scheduler.register(registration);
            Instant first = scheduler
                    .registration("ws", "engine-schedule")
                    .orElseThrow()
                    .nextFireTime();
            scheduler.register(registration);
            assertEquals(
                    first,
                    scheduler
                            .registration("ws", "engine-schedule")
                            .orElseThrow()
                            .nextFireTime());
            assertEquals(2, jdbc.queryForObject("SELECT MISFIRE_INSTR FROM YAK_QRTZ_TRIGGERS", Integer.class));
            scheduler.start();
            ScheduleTrigger fired = notifications.poll(15, TimeUnit.SECONDS);
            assertNotNull(fired);
            scheduler.standby();
            scheduler.register(
                    new ScheduleRegistration("ws", "engine-schedule", 8, "0 * * * * ?", "UTC", Instant.now()));
            assertEquals(7, fired.revision());
            assertEquals(registration.activatedAt(), fired.activatedAt());
            assertTrue(!fired.scheduledTime().isBefore(registration.activatedAt()));
            assertEquals(
                    8,
                    scheduler
                            .registration("ws", "engine-schedule")
                            .orElseThrow()
                            .registration()
                            .revision());
        }
        try (QuartzScheduler restored = new QuartzScheduler(dataSource, notifications::add, Duration.ofSeconds(60))) {
            restored.initialize();
            assertEquals(
                    8,
                    restored.registration("ws", "engine-schedule")
                            .orElseThrow()
                            .registration()
                            .revision());
            String longCron = "0 0 " + String.join(",", java.util.Collections.nCopies(70, "1")) + " * * ?";
            restored.register(new ScheduleRegistration("ws", "long-cron", 1, longCron, "UTC", Instant.now()));
            assertEquals(
                    longCron,
                    restored.registration("ws", "long-cron")
                            .orElseThrow()
                            .registration()
                            .cronExpression());
            restored.remove("ws", "engine-schedule");
            assertTrue(restored.registration("ws", "engine-schedule").isEmpty());
        }
    }

    private static int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static ScheduleTrigger trigger() {
        return triggerAt(BASE.plusSeconds(60));
    }

    private static ScheduleTrigger triggerAt(Instant due) {
        return new ScheduleTrigger("ws", "schedule", 2, due, due, BASE);
    }

    private static void insertDefinition() {
        jdbc.update(
                "INSERT INTO yak_ops_task_definition (id,workspace_id,name,task_type,status,current_version,published_version,published_debug,create_time,update_time,create_by,update_by) VALUES ('task','ws','test task','SCHEDULE_TEST',1,1,1,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),'actor','actor')");
        jdbc.update(
                "INSERT INTO yak_ops_task_definition_version (id,workspace_id,definition_id,version,parameters,create_time,update_time,create_by,update_by) VALUES ('version','ws','task',1,'{}',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),'actor','actor')");
    }

    private static void saveNextDraft(boolean debug) {
        jdbc.update(
                "INSERT INTO yak_ops_task_definition_version (id,workspace_id,definition_id,version,parameters,create_time,update_time,create_by,update_by) VALUES ('draft-version','ws','task',2,'{}',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),'actor','actor')");
        jdbc.update("UPDATE yak_ops_task_definition SET current_version=2,debug=?,revision=revision+1 WHERE id='task'", debug);
    }

    @Test
    void manualConflictWaitsUntilReleasedAndNeverDispatchesEarly() {
        PLUGIN.resources = List.of(new ExecutionResource("TEST", "shared"));
        occupy("shared");
        SubmissionResult submitted = submissions.submit(new TaskSubmissionCommand(
                "ws", "task", TaskTriggerType.MANUAL, "manual-wait-01", "actor", null, null, null));
        assertEquals(TaskInstanceStatus.SUBMITTED, submitted.status());
        assertEquals(-1, jdbc.queryForObject(
                "SELECT status FROM yak_ops_task_group_queue WHERE task_id = ?",
                Integer.class,
                submitted.instanceId()));
        InstanceRepository instances = context.getBean(InstanceRepository.class);
        assertFalse(instances.pending().stream().anyMatch(row -> submitted.instanceId().equals(row.getId())));

        TaskGroupQueueRepository groups = context.getBean(TaskGroupQueueRepository.class);
        assertFalse(groups.tryAcquire("ws", submitted.instanceId()));
        groups.releaseOwned("ws", "other-instance");
        assertTrue(groups.tryAcquire("ws", submitted.instanceId()));
        assertTrue(groups.allAcquired("ws", submitted.instanceId()));
        assertTrue(instances.pending().stream().anyMatch(row -> submitted.instanceId().equals(row.getId())));
    }

    @Test
    void waitingQueuesHonorPriorityAndThenFifo() {
        occupy("shared");
        enqueue("low", "shared", 1);
        enqueue("high", "shared", 10);
        TaskGroupQueueRepository groups = context.getBean(TaskGroupQueueRepository.class);
        assertEquals("high", groups.waiting(10, null).get(0).getInstanceId());

        groups.releaseOwned("ws", "other-instance");
        assertFalse(groups.tryAcquire("ws", "low"));
        assertTrue(groups.tryAcquire("ws", "high"));
        assertFalse(groups.tryAcquire("ws", "low"));
        groups.releaseOwned("ws", "high");
        assertTrue(groups.tryAcquire("ws", "low"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT use_size FROM yak_ops_task_group WHERE resource_key = 'shared'", Integer.class));
    }

    @Test
    void multiResourceWaitingNeverOccupiesOnlyTheFreeGroup() {
        occupy("z-busy");
        enqueue("multi", "a-free", 0);
        TaskGroupQueueEntity second = new TaskGroupQueueEntity();
        second.setWorkspaceId("ws");
        second.setResourceType("TEST");
        second.setResourceKey("z-busy");
        second.setInstanceId("multi");
        second.initCreate("actor");
        TaskGroupQueueRepository groups = context.getBean(TaskGroupQueueRepository.class);
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
                .executeWithoutResult(status -> groups.add(second));

        assertFalse(groups.tryAcquire("ws", "multi"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT use_size FROM yak_ops_task_group WHERE resource_key = 'a-free'", Integer.class));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_task_group_queue WHERE task_id = 'multi' AND status = -1",
                Integer.class));
        groups.releaseOwned("ws", "other-instance");
        assertTrue(groups.tryAcquire("ws", "multi"));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_task_group_queue WHERE task_id = 'multi' AND status = 1",
                Integer.class));
    }

    @Test
    void waitingQueueKeysetPagesPastThousandBlockedTasksToFreeResources() {
        occupy("blocked");
        for (int i = 0; i < 1000; i++) {
            enqueue("blocked-" + i, "blocked", 10);
        }
        enqueue("free-resource", "free", 0);
        TaskGroupQueueRepository groups = context.getBean(TaskGroupQueueRepository.class);
        List<TaskGroupQueueEntity> firstPage = groups.waiting(1000, null);
        assertEquals(1000, firstPage.size());
        assertTrue(firstPage.stream().noneMatch(row -> "free-resource".equals(row.getInstanceId())));

        List<TaskGroupQueueEntity> secondPage = groups.waiting(1000, firstPage.get(firstPage.size() - 1));
        assertEquals(1, secondPage.size());
        assertEquals("free-resource", secondPage.get(0).getInstanceId());
        assertTrue(groups.tryAcquire("ws", "free-resource"));
        assertFalse(groups.tryAcquire("ws", "blocked-0"));
    }

    @Test
    void registryPurgePreservesClientRegisteredAfterHeartbeatSnapshot() throws Exception {
        JdbcRegistryClientRepository clients = context.getBean(JdbcRegistryClientRepository.class);
        JdbcRegistryClientRepository snapshotClients = org.mockito.Mockito.spy(clients);
        JdbcRegistryDataRepository data = context.getBean(JdbcRegistryDataRepository.class);
        JdbcRegistryDataChangeEventRepository events =
                context.getBean(JdbcRegistryDataChangeEventRepository.class);
        JdbcRegistryLockRepository locks = context.getBean(JdbcRegistryLockRepository.class);
        JdbcRegistryProperties properties = new JdbcRegistryProperties();
        properties.setSessionTimeout(Duration.ofSeconds(60));
        JdbcRegistryServer server = new JdbcRegistryServer(
                data, locks, snapshotClients, events, properties,
                new TransactionTemplate(context.getBean(PlatformTransactionManager.class)));
        try {
            jdbc.update(
                    "INSERT INTO yak_ops_jdbc_registry_data (data_key,data_value,data_type,client_id)"
                            + " VALUES ('/test/orphan','orphan','EPHEMERAL',70001)");
            jdbc.update(
                    "INSERT INTO yak_ops_jdbc_registry_lock (lock_key,lock_owner,client_id)"
                            + " VALUES ('/test/orphan-lock','orphan',70001)");
            // Simulate the exact race: B registers AFTER the purge reads its
            // heartbeat snapshot, but BEFORE it reads metadata and locks.
            org.mockito.Mockito.doAnswer(invocation -> {
                Object heartbeatSnapshot = invocation.callRealMethod();
                jdbc.update(
                        "INSERT INTO yak_ops_jdbc_registry_client_heartbeat"
                                + " (id,client_name,last_heartbeat_time,connection_config)"
                                + " VALUES (70002,'new-client',?,'{}')",
                        System.currentTimeMillis());
                jdbc.update(
                        "INSERT INTO yak_ops_jdbc_registry_data (data_key,data_value,data_type,client_id)"
                                + " VALUES ('/test/live','live','EPHEMERAL',70002)");
                jdbc.update(
                        "INSERT INTO yak_ops_jdbc_registry_lock (lock_key,lock_owner,client_id)"
                                + " VALUES ('/test/live-lock','live',70002)");
                return heartbeatSnapshot;
            }).when(snapshotClients).queryAll();

            Method purge = JdbcRegistryServer.class.getDeclaredMethod("purgeInvalidJdbcRegistryMetadata");
            purge.setAccessible(true);
            purge.invoke(server);

            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM yak_ops_jdbc_registry_data WHERE data_key='/test/live'", Integer.class));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM yak_ops_jdbc_registry_lock WHERE lock_key='/test/live-lock'", Integer.class));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM yak_ops_jdbc_registry_data WHERE data_key='/test/orphan'", Integer.class));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM yak_ops_jdbc_registry_lock WHERE lock_key='/test/orphan-lock'", Integer.class));
            assertTrue(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM yak_ops_jdbc_registry_data_change_event WHERE event_type='DELETE'",
                    Integer.class) >= 1);
        } finally {
            server.close();
            jdbc.update("DELETE FROM yak_ops_jdbc_registry_data_change_event");
            jdbc.update("DELETE FROM yak_ops_jdbc_registry_lock");
            jdbc.update("DELETE FROM yak_ops_jdbc_registry_data");
            jdbc.update("DELETE FROM yak_ops_jdbc_registry_client_heartbeat");
        }
    }

    private static void enqueue(String instanceId, String resource, int priority) {
        jdbc.update(
                "INSERT INTO yak_ops_task_instance"
                        + " (id,workspace_id,definition_id,definition_version,task_type,trigger_type,"
                        + " request_id,request_fingerprint,resource_revision,status,state_version,"
                        + " create_time,update_time,create_by,update_by)"
                        + " VALUES (?, 'ws', 'task', 1, 'SCHEDULE_TEST', 'MANUAL', ?, 'MANUAL:task', 'v1',"
                        + " 'SUBMITTED', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 'actor', 'actor')",
                instanceId,
                "request-" + instanceId);
        TaskGroupQueueEntity queue = new TaskGroupQueueEntity();
        queue.setWorkspaceId("ws");
        queue.setResourceType("TEST");
        queue.setResourceKey(resource);
        queue.setInstanceId(instanceId);
        queue.setPriority(priority);
        queue.initCreate("actor");
        TaskGroupQueueRepository groups = context.getBean(TaskGroupQueueRepository.class);
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
                .executeWithoutResult(status -> groups.add(queue));
    }

    private static void occupy(String resource) {
        jdbc.update(
                "INSERT INTO yak_ops_task_group"
                        + " (name,workspace_id,resource_type,resource_key,group_size,use_size,status,create_time,update_time)"
                        + " VALUES ('TEST','ws','TEST',?,1,1,1,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
                resource);
        Long groupId = jdbc.queryForObject(
                "SELECT id FROM yak_ops_task_group WHERE workspace_id='ws' AND resource_type='TEST' AND resource_key=?",
                Long.class,
                resource);
        jdbc.update(
                "INSERT INTO yak_ops_task_group_queue"
                        + " (id,task_id,group_id,priority,status,force_start,in_queue,create_time,update_time)"
                        + " VALUES ('occupied','other-instance',?,0,1,0,1,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
                groupId);
    }

    private static void stop(boolean unpublish) {
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            DefinitionRepository definitions = context.getBean(DefinitionRepository.class);
            DefinitionEntity definition =
                    definitions.queryByIdForUpdate("ws", "task").orElseThrow();
            ScheduleRepository schedules = context.getBean(ScheduleRepository.class);
            schedules.disableForTarget(
                    "ws", ScheduleTargetType.TASK, "task", LocalDateTime.now(ZoneOffset.UTC), "actor");
            if (unpublish) {
                definition.setStatus(DefinitionStatus.UNPUBLISHED);
                definition.setPublishedVersion(null);
                definition.setPublishedDebug(null);
                definitions.update("ws", definition);
            }
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan({"io.yak.ops.dao.mapper", "org.apache.dolphinscheduler.plugin.registry.jdbc.mapper"})
    static class Beans {
        @Bean
        DataSource dataSource() {
            return dataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate() {
            return jdbc;
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new JdbcTransactionManager(dataSource);
        }

        @Bean
        Clock scheduleClock() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return NOW.get();
                }
            };
        }

        @Bean
        ScheduleAdmissionController scheduleAdmissionController(Clock scheduleClock) {
            return new ScheduleAdmissionController(scheduleClock, Duration.ofSeconds(60));
        }

        @Bean
        Registry registry() {
            return REGISTRY;
        }

        @Bean
        WorkspaceService workspaceService() {
            return AUTHORITY;
        }

        @Bean
        TaskPluginManager taskPluginRegistry() {
            return new TaskPluginManager(List.of(PLUGIN));
        }

        @Bean
        TaskValidationContextFactory taskValidationContextFactory() {
            return workspace -> mock(TaskValidationContext.class);
        }

        @Bean
        CronService cronService() {
            return new QuartzCronService();
        }

        @Bean
        SqlSessionFactory sqlSessionFactory() throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setCacheEnabled(false);
            factory.setConfiguration(configuration);
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
            factory.setPlugins(interceptor);
            return factory.getObject();
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }
    }

    private static final class TestPlugin implements TaskChannel {
        private final AtomicInteger plans = new AtomicInteger();
        private volatile List<ExecutionResource> resources;
        private volatile int blockCall;
        private volatile boolean changeBinding;
        private CountDownLatch entered;
        private CountDownLatch release;

        void reset() {
            plans.set(0);
            resources = List.of(new ExecutionResource("TEST", "target"));
            blockCall = 0;
            changeBinding = false;
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        @Override
        public String type() {
            return "SCHEDULE_TEST";
        }

        @Override
        public int parameterSchemaVersion() {
            return 1;
        }

        @Override
        public OptionRule optionRule() {
            return OptionRule.builder().build();
        }

        @Override
        public AbstractParameters parseParameters(String content) {
            return new AbstractParameters() {
                @Override
                public boolean checkParameters() {
                    return true;
                }
            };
        }

        @Override
        public ReadonlyConfig parse(String content) {
            return ReadonlyConfig.fromMap(Map.of());
        }

        @Override
        public void validateReferences(ReadonlyConfig parameters, TaskValidationContext validation) {}

        @Override
        public void validate(ReadonlyConfig parameters, TaskValidationContext validation) {}

        @Override
        public boolean supportsScheduling(ReadonlyConfig parameters) {
            return true;
        }

        @Override
        public TaskExecutionResources plan(ReadonlyConfig parameters, TaskExecutionIdentity identity) {
            int call = plans.incrementAndGet();
            if (call == blockCall) {
                entered.countDown();
                try {
                    assertTrue(release.await(15, TimeUnit.SECONDS));
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
            }
            String revision = "binding-v1";
            if (changeBinding && call > 1) {
                revision = "binding-v2";
            }
            return new TaskExecutionResources(resources, revision);
        }

        @Override
        public CompletionStage<TaskHandle> createTask(
                ReadonlyConfig parameters, TaskExecutionResources plan, TaskExecutionIdentity identity, boolean debug) {
            throw new AssertionError("Admission must not start physical execution");
        }
    }
}
