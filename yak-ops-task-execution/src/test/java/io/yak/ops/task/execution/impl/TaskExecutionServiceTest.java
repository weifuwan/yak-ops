package io.yak.ops.task.execution.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.task.submission.SubmissionResult;
import io.yak.ops.business.task.submission.SubmissionService;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.task.execution.dispatch.EmbeddedTaskDispatcher;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Verifies durable admission and idempotent dispatch behavior at the Task execution boundary. */
class TaskExecutionServiceTest {

    private final TaskExecutionServiceImpl service = new TaskExecutionServiceImpl();
    private final SubmissionService submissions = mock(SubmissionService.class);
    private final EmbeddedTaskDispatcher dispatcher = mock(EmbeddedTaskDispatcher.class);

    TaskExecutionServiceTest() {
        inject("submissions", submissions);
        inject("dispatcher", dispatcher);
        WorkspaceContext.bind("ws");
    }

    @AfterEach
    void unbindWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void dispatchesNewSubmissionAfterAdmission() {
        SubmissionResult result = new SubmissionResult("i", "a", 2, TaskInstanceStatus.SUBMITTED, false);
        when(submissions.submitManual("d", "request-123", "user")).thenReturn(result);

        assertEquals(result, service.submitManual("d", "request-123", "user"));

        verify(dispatcher).dispatch("ws", "i");
    }

    @Test
    void idempotentReplayDoesNotDispatchAgain() {
        SubmissionResult result = new SubmissionResult("i", "a", 2, TaskInstanceStatus.RUNNING, true);
        when(submissions.submitManual("d", "request-123", "user")).thenReturn(result);

        assertEquals(result, service.submitManual("d", "request-123", "user"));

        verify(dispatcher, never()).dispatch("ws", "i");
    }

    @Test
    void cancellationUsesTheDispatcherFence() {
        when(dispatcher.cancel("ws", "i")).thenReturn(TaskInstanceStatus.CANCELLING);
        assertEquals(TaskInstanceStatus.CANCELLING, service.cancel("ws", "i"));
    }

    private void inject(String fieldName, Object dependency) {
        try {
            Field field = TaskExecutionServiceImpl.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(service, dependency);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
