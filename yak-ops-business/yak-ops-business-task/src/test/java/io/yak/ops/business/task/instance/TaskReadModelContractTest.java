package io.yak.ops.business.task.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.task.instance.impl.InstanceServiceImpl;
import io.yak.ops.business.task.log.impl.LogServiceImpl;
import io.yak.ops.business.task.schedule.impl.ScheduleServiceImpl;
import io.yak.ops.common.bean.dto.task.InstanceQueryDTO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.task.AttemptStatus;
import io.yak.ops.common.enums.task.InstanceStatus;
import io.yak.ops.common.enums.task.ScheduleTargetType;
import io.yak.ops.common.enums.task.TriggerType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.dao.entity.task.AttemptEntity;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.EventEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.entity.task.ScheduleEntity;
import io.yak.ops.dao.repository.task.AttemptRepository;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.EventRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import io.yak.ops.dao.repository.task.ScheduleRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 统一Task只读模型的Workspace隔离、日志URI遮蔽、Attempt历史与调度目标测试。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
class TaskReadModelContractTest {

    private final InstanceRepository instances = mock(InstanceRepository.class);
    private final AttemptRepository attempts = mock(AttemptRepository.class);

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void shouldExposeHistoricalDataSyncAsGenericTaskInstanceWithoutCredentials() {
        WorkspaceContext.bind("workspace-1");
        InstanceEntity instance = instance();
        when(instances.queryById("workspace-1", "instance-1")).thenReturn(Optional.of(instance));
        when(instances.queryPage(eq("workspace-1"), any()))
                .thenReturn(PageData.of(List.of(instance), 1, 1, 10));
        InstanceServiceImpl service = instanceService();

        assertEquals("DATA_SYNC", service.query("instance-1").getTaskType());
        assertEquals("SCHEDULE", service.query("instance-1").getTriggerType());
        assertEquals("RUNNING", service.query("instance-1").getStatus());
        assertFalse(JSONUtils.toJson(service.query("instance-1")).contains("password"));

        InstanceQueryDTO query = new InstanceQueryDTO();
        query.setTaskType("data-sync");
        assertEquals(1, service.queryPage(query).getBizData().size());
        verify(instances).queryPage(eq("workspace-1"), org.mockito.ArgumentMatchers.argThat(
                value -> "DATA_SYNC".equals(value.taskType()) && value.pageSize() == 10));
    }

    @Test
    void shouldVerifyParentWorkspaceBeforeExposingAttemptsAndLogLocations() {
        WorkspaceContext.bind("workspace-1");
        InstanceServiceImpl service = instanceService();
        assertThrows(BusinessException.class, () -> service.queryAttempts("instance-1"));
        verify(attempts, never()).queryByInstance(any(), any());

        when(instances.queryById("workspace-1", "instance-1")).thenReturn(Optional.of(instance()));
        AttemptEntity attempt = new AttemptEntity();
        attempt.setId("attempt-1");
        attempt.setInstanceId("instance-1");
        attempt.setAttemptNo(1);
        attempt.setStatus(AttemptStatus.FAILED);
        attempt.setLogUri("file:///private/worker/password");
        when(attempts.queryByInstance("workspace-1", "instance-1")).thenReturn(List.of(attempt));

        var history = service.queryAttempts("instance-1");
        assertEquals(1, history.size());
        assertEquals("FAILED", history.getFirst().getStatus());
        assertTrue(history.getFirst().getLogAvailable());
        assertFalse(JSONUtils.toJson(history).contains("file:///"));
    }

    @Test
    void shouldRejectCrossWorkspaceEventsWithoutQueryingHistory() {
        WorkspaceContext.bind("workspace-b");
        EventRepository events = mock(EventRepository.class);
        LogServiceImpl service = new LogServiceImpl();
        inject(service, "instanceRepository", instances);
        inject(service, "eventRepository", events);

        assertThrows(BusinessException.class, () -> service.queryEvents("instance-1"));
        verify(events, never()).queryByInstance(any(), any());

        WorkspaceContext.bind("workspace-1");
        when(instances.queryById("workspace-1", "instance-1")).thenReturn(Optional.of(instance()));
        EventEntity event = new EventEntity();
        event.setId("event-1");
        event.setInstanceId("instance-1");
        event.setEventType(6);
        event.setMessage("Attempt failed");
        when(events.queryByInstance("workspace-1", "instance-1")).thenReturn(List.of(event));
        assertEquals(6, service.queryEvents("instance-1").getFirst().getEventType());
    }

    @Test
    void shouldReadOnlyTaskScheduleWithCanonicalTargetScope() {
        WorkspaceContext.bind("workspace-1");
        DefinitionRepository definitions = mock(DefinitionRepository.class);
        ScheduleRepository schedules = mock(ScheduleRepository.class);
        ScheduleServiceImpl service = new ScheduleServiceImpl();
        inject(service, "definitionRepository", definitions);
        inject(service, "scheduleRepository", schedules);
        assertThrows(BusinessException.class, () -> service.queryTaskSchedule("task-1"));

        DefinitionEntity definition = new DefinitionEntity();
        definition.setId("task-1");
        when(definitions.queryById("workspace-1", "task-1")).thenReturn(Optional.of(definition));
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setId("schedule-1");
        schedule.setTargetType(ScheduleTargetType.TASK);
        schedule.setTargetId("task-1");
        schedule.setCronExpression("0 0 0 * * ?");
        schedule.setEnabled(true);
        when(schedules.queryByTarget("workspace-1", ScheduleTargetType.TASK, "task-1"))
                .thenReturn(Optional.of(schedule));
        assertEquals("TASK", service.queryTaskSchedule("task-1").getTargetType());
        assertEquals("task-1", service.queryTaskSchedule("task-1").getTargetId());
    }

    private InstanceServiceImpl instanceService() {
        InstanceServiceImpl service = new InstanceServiceImpl();
        inject(service, "instanceRepository", instances);
        inject(service, "attemptRepository", attempts);
        return service;
    }

    private InstanceEntity instance() {
        InstanceEntity instance = new InstanceEntity();
        instance.setId("instance-1");
        instance.setWorkspaceId("workspace-1");
        instance.setTaskId("task-1");
        instance.setTaskType("DATA_SYNC");
        instance.setTaskName("orders");
        instance.setTaskVersion(3);
        instance.setStatus(InstanceStatus.RUNNING);
        instance.setTriggerType(TriggerType.SCHEDULE);
        instance.setDefinitionSnapshot("{\"password\":\"very-secret\"}");
        return instance;
    }

    private void inject(Object target, String fieldName, Object dependency) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, dependency);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
