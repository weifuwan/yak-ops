package io.yak.ops.business.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.yak.ops.business.datasync.scheduler.DataSyncScheduleDefinition;
import io.yak.ops.business.datasync.scheduler.ScheduleEngine;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskOperationVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncDesiredState;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncTriggerType;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.entity.datasync.DataSyncScheduleEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncScheduleRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTaskPageQuery;
import io.yak.ops.dao.repository.datasync.DataSyncTaskRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataSyncOperationReadModelTest {

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void shouldAggregatePublishedTaskLatestExecutionAndSchedulerNextFireTime() throws Exception {
        DataSyncOperationsServiceImpl service = new DataSyncOperationsServiceImpl();
        DataSyncTaskEntity task = task();
        DataSyncInstanceEntity latest = latestExecution();
        DataSyncScheduleEntity schedule = schedule();
        AtomicReference<DataSyncTaskPageQuery> capturedQuery = new AtomicReference<>();

        inject(service, "taskRepository", taskRepository(task, capturedQuery));
        inject(service, "instanceRepository", instanceRepository(latest));
        inject(service, "scheduleRepository", scheduleRepository(schedule));
        inject(service, "scheduleEngine", scheduleEngine());

        DataSyncTaskQueryDTO query = new DataSyncTaskQueryDTO();
        query.setPageNo(1);
        query.setPageSize(20);
        query.setSyncType(DataSyncType.OFFLINE);
        query.setStatus(DataSyncTaskStatus.UNPUBLISHED);

        WorkspaceContext.bind("workspace-1");
        PagingData<DataSyncTaskOperationVO> result = service.queryTaskOperationPage(query);

        assertEquals(DataSyncTaskStatus.PUBLISHED, capturedQuery.get().status());
        assertEquals(1, result.getBizData().size());

        DataSyncTaskOperationVO row = result.getBizData().getFirst();
        assertEquals("task-1", row.getId());
        assertEquals("execution-1", row.getLatestInstance().getId());
        assertEquals(DataSyncInstanceStatus.RETRY_WAITING.name(), row.getLatestInstance().getStatus());
        assertNotNull(row.getSchedule());
        assertEquals("2026-10-01T02:00", row.getSchedule().getNextFireTime().toString());
    }

    private DataSyncTaskEntity task() {
        DataSyncTaskEntity task = new DataSyncTaskEntity();
        task.setId("task-1");
        task.setWorkspaceId("workspace-1");
        task.setName("offline-task");
        task.setSyncType(DataSyncType.OFFLINE);
        task.setStatus(DataSyncTaskStatus.PUBLISHED);
        task.setDesiredState(DataSyncDesiredState.STOPPED);
        task.setRetryPolicy("{\"maxAttempts\":3,\"backoffSeconds\":60}");
        task.setDefinitionVersion(2);
        return task;
    }

    private DataSyncInstanceEntity latestExecution() {
        DataSyncInstanceEntity execution = new DataSyncInstanceEntity();
        execution.setId("execution-1");
        execution.setWorkspaceId("workspace-1");
        execution.setTaskId("task-1");
        execution.setTaskName("offline-task");
        execution.setTaskVersion(2);
        execution.setSyncType(DataSyncType.OFFLINE);
        execution.setTriggerType(DataSyncTriggerType.SCHEDULE);
        execution.setStatus(DataSyncInstanceStatus.RETRY_WAITING);
        execution.setCurrentAttempt(2);
        execution.setMaxAttempts(3);
        execution.setBackoffSeconds(60);
        execution.setReadRows(10L);
        execution.setWriteRows(8L);
        return execution;
    }

    private DataSyncScheduleEntity schedule() {
        DataSyncScheduleEntity schedule = new DataSyncScheduleEntity();
        schedule.setId("schedule-1");
        schedule.setWorkspaceId("workspace-1");
        schedule.setTaskId("task-1");
        schedule.setCronExpression("0 0 2 * * ?");
        schedule.setTimeZone("Asia/Shanghai");
        schedule.setEnabled(true);
        return schedule;
    }

    private DataSyncTaskRepository taskRepository(
            DataSyncTaskEntity task, AtomicReference<DataSyncTaskPageQuery> capturedQuery) {
        return (DataSyncTaskRepository) Proxy.newProxyInstance(
                DataSyncTaskRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncTaskRepository.class},
                (proxy, method, args) -> {
                    if ("queryPage".equals(method.getName())) {
                        capturedQuery.set((DataSyncTaskPageQuery) args[1]);
                        return PageData.of(List.of(task), 1, 1, 20);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncInstanceRepository instanceRepository(DataSyncInstanceEntity latest) {
        return (DataSyncInstanceRepository) Proxy.newProxyInstance(
                DataSyncInstanceRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncInstanceRepository.class},
                (proxy, method, args) -> {
                    if ("queryLatestByTask".equals(method.getName())) return Optional.of(latest);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncScheduleRepository scheduleRepository(DataSyncScheduleEntity schedule) {
        return (DataSyncScheduleRepository) Proxy.newProxyInstance(
                DataSyncScheduleRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncScheduleRepository.class},
                (proxy, method, args) -> {
                    if ("queryByTask".equals(method.getName())) return Optional.of(schedule);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private ScheduleEngine scheduleEngine() {
        return new ScheduleEngine() {
            @Override
            public void validate(DataSyncScheduleDefinition definition) {}

            @Override
            public void schedule(DataSyncScheduleDefinition definition) {}

            @Override
            public void reschedule(DataSyncScheduleDefinition definition) {}

            @Override
            public void unschedule(String scheduleId) {}

            @Override
            public Optional<Instant> queryNextFireTime(String scheduleId) {
                return Optional.of(Instant.parse("2026-09-30T18:00:00Z"));
            }

            @Override
            public List<Instant> previewNextFireTimes(
                    String cronExpression, java.time.ZoneId timeZone, int count) {
                return List.of();
            }
        };
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        DataSyncTestServices.inject(target, fieldName, value);
    }
}
