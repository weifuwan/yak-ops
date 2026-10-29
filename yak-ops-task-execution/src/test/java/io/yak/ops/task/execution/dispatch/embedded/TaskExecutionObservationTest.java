package io.yak.ops.task.execution.dispatch.embedded;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import io.yak.ops.business.task.instance.DispatchTicket;
import io.yak.ops.business.task.instance.InstanceLifecycleService;
import io.yak.ops.business.task.instance.InstanceService;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.worker.executor.PhysicalTaskEngineDelegator;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** Acknowledging a terminal physical task requires the matching persisted business state. */
class TaskExecutionObservationTest {
    private static final DispatchTicket TICKET =
            new DispatchTicket("ws", "instance", "task", 1, "DATA_SYNC", "revision", "token", false);

    @Test
    void unavailableMalformedAndOversizedReportsDoNotBlockConfirmedCompletion() throws Exception {
        for (String report : new String[] {"throw", "[]", "{", " ".repeat(262145)}) {
            try (TaskExecutionObserver observer = new TaskExecutionObserver()) {
                InstanceLifecycleService lifecycle = mock(InstanceLifecycleService.class);
                InstanceService instances = mock(InstanceService.class);
                InstanceEntity committed = instance(TaskInstanceStatus.CANCELED, "token");
                when(instances.instance("ws", "instance")).thenReturn(committed);
                inject(observer, "lifecycle", lifecycle);
                inject(observer, "instances", instances);
                TaskHandle handle = mock(TaskHandle.class);
                if ("throw".equals(report)) {
                    when(handle.executionReport()).thenThrow(new IllegalStateException("Unavailable"));
                } else {
                    when(handle.executionReport()).thenReturn(Optional.of(report));
                }
                assertTrue(observeTerminal(observer, handle, TaskOutcome.CANCELED));
                verify(lifecycle).complete(TICKET, TaskOutcome.CANCELED);
                verify(lifecycle, never()).uncertain(any());
            }
        }
    }

    @Test
    void reconcilingInstanceDoesNotAcknowledgeAStaleSuccess() throws Exception {
        try (TaskExecutionObserver observer = new TaskExecutionObserver()) {
            InstanceLifecycleService lifecycle = mock(InstanceLifecycleService.class);
            InstanceService instances = mock(InstanceService.class);
            when(instances.instance("ws", "instance")).thenReturn(instance(TaskInstanceStatus.RECONCILING, "token"));
            inject(observer, "lifecycle", lifecycle);
            inject(observer, "instances", instances);

            TaskHandle handle = mock(TaskHandle.class);
            when(handle.executionReport()).thenReturn(Optional.empty());
            assertFalse(observeTerminal(observer, handle, TaskOutcome.FINISHED));
            verify(lifecycle).complete(TICKET, TaskOutcome.FINISHED);
        }
    }

    @Test
    void terminalInstanceFromAnotherDispatchIsNotAcknowledged() throws Exception {
        try (TaskExecutionObserver observer = new TaskExecutionObserver()) {
            InstanceLifecycleService lifecycle = mock(InstanceLifecycleService.class);
            InstanceService instances = mock(InstanceService.class);
            when(instances.instance("ws", "instance")).thenReturn(instance(TaskInstanceStatus.FINISHED, "other-token"));
            inject(observer, "lifecycle", lifecycle);
            inject(observer, "instances", instances);

            TaskHandle handle = mock(TaskHandle.class);
            when(handle.executionReport()).thenReturn(Optional.empty());
            assertFalse(observeTerminal(observer, handle, TaskOutcome.FINISHED));
        }
    }

    @Test
    void handleRegistrationFailureMarksPhysicalSubmissionUncertain() throws Exception {
        try (TaskExecutionObserver observer = new TaskExecutionObserver()) {
            InstanceLifecycleService lifecycle = mock(InstanceLifecycleService.class);
            PhysicalTaskEngineDelegator worker = mock(PhysicalTaskEngineDelegator.class);
            inject(observer, "lifecycle", lifecycle);
            inject(observer, "worker", worker);
            TaskHandle handle = mock(TaskHandle.class);
            doThrow(new IllegalStateException("Duplicate physical handle"))
                    .when(worker).registerTaskHandle("instance", handle);

            CompletableFuture<TaskOutcome> result = observer
                    .observeTracked(TICKET, CompletableFuture.completedFuture(handle))
                    .toCompletableFuture();
            org.junit.jupiter.api.Assertions.assertThrows(
                    java.util.concurrent.CompletionException.class, result::join);
            verify(lifecycle, timeout(3000)).uncertain(TICKET);
            verify(handle).cancel();
            verify(handle, never()).start();
        }
    }

    private static boolean observeTerminal(TaskExecutionObserver observer, TaskHandle handle, TaskOutcome outcome)
            throws Exception {
        Method method = TaskExecutionObserver.class.getDeclaredMethod(
                "observeTerminal", DispatchTicket.class, TaskHandle.class, TaskOutcome.class, Throwable.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(observer, TICKET, handle, outcome, null);
    }

    private static InstanceEntity instance(TaskInstanceStatus status, String dispatchToken) {
        InstanceEntity entity = new InstanceEntity();
        entity.setStatus(status);
        entity.setDispatchToken(dispatchToken);
        return entity;
    }

    private static void inject(TaskExecutionObserver observer, String name, Object value) throws Exception {
        Field field = TaskExecutionObserver.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(observer, value);
    }
}
