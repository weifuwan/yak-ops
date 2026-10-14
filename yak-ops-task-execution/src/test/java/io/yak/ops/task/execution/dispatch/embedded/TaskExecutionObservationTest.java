package io.yak.ops.task.execution.dispatch.embedded;

import static org.mockito.Mockito.*;
import io.yak.ops.business.task.instance.DispatchTicket;
import io.yak.ops.business.task.instance.InstanceLifecycleService;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Observation failures cannot prevent an authoritative outcome from releasing execution resources. */
class TaskExecutionObservationTest {
    @Test
    void unavailableMalformedAndOversizedReportsDoNotBlockCompletion() throws Exception {
        for (String report : new String[] {"throw", "[]", "{", " ".repeat(262145)}) {
            try (var observer = new TaskExecutionObserver()) {
                var lifecycle = mock(InstanceLifecycleService.class);
                var field = TaskExecutionObserver.class.getDeclaredField("lifecycle");
                field.setAccessible(true);
                field.set(observer, lifecycle);
                var handle = mock(TaskHandle.class);
                if ("throw".equals(report)) when(handle.executionReport()).thenThrow(new IllegalStateException("Unavailable"));
                else when(handle.executionReport()).thenReturn(Optional.of(report));
                var ticket = new DispatchTicket("ws", "instance", "task", 1, "DATA_SYNC", "revision", "token", false);
                var method = TaskExecutionObserver.class.getDeclaredMethod("observeTerminal", DispatchTicket.class, TaskHandle.class, TaskOutcome.class, Throwable.class);
                method.setAccessible(true);
                method.invoke(observer, ticket, handle, TaskOutcome.CANCELED, null);
                verify(lifecycle).complete(ticket, TaskOutcome.CANCELED);
                verify(lifecycle, never()).uncertain(any());
            }
        }
    }
}
