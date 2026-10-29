package io.yak.ops.task.execution.dispatch.embedded;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.task.instance.DispatchTicket;
import io.yak.ops.business.task.instance.InstanceLifecycleService;
import io.yak.ops.business.task.instance.InstanceService;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.worker.executor.PhysicalTaskEngineDelegator;
import java.util.concurrent.atomic.AtomicReference;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.plugin.task.api.execution.TaskRuntimeReference;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Ensures platform registration precedes DDL and uncertain outcomes retain public partial observations. */
class TaskExecutionSchemaObservationTest {
    @Test
    void registersBeforeActivationAndPersistsTheLateRealJobId() throws Exception {
        try (TaskExecutionObserver observer = new TaskExecutionObserver()) {
            InstanceLifecycleService lifecycle = mock(InstanceLifecycleService.class);
            InstanceService instances = mock(InstanceService.class);
            inject(observer, "lifecycle", lifecycle);
            inject(observer, "instances", instances);
            PhysicalTaskEngineDelegator worker = mock(PhysicalTaskEngineDelegator.class);
            AtomicReference<TaskHandle> activeHandle = new AtomicReference<>();
            when(worker.taskHandle("instance")).thenAnswer(call -> Optional.ofNullable(activeHandle.get()));
            inject(observer, "worker", worker);
            InstanceEntity instance = new InstanceEntity();
            instance.setStatus(TaskInstanceStatus.RUNNING);
            instance.setDispatchToken("token");
            when(instances.instance("ws", "instance")).thenReturn(instance);
            DispatchTicket ticket = new DispatchTicket("ws", "instance", "task", 1, "DATA_SYNC", "revision", "token", false);
            AtomicBoolean persisted = new AtomicBoolean();
            CompletableFuture<TaskOutcome> completed = new CompletableFuture<>();
            CompletableFuture<Optional<TaskRuntimeReference>> reference = new CompletableFuture<>();
            TaskHandle handle = new TaskHandle() {
                @Override public Optional<String> executionReport() { return Optional.of("{\"version\":3}"); }
                @Override public Optional<TaskRuntimeReference> runtimeReference() { return reference.getNow(Optional.empty()); }
                @Override public CompletionStage<Optional<TaskRuntimeReference>> runtimeReferenceReady() { return reference; }
                @Override public CompletionStage<TaskOutcome> completion() { return completed; }
                @Override public CompletionStage<Void> cancel() { return CompletableFuture.completedFuture(null); }
                @Override public void start() {
                    assertTrue(persisted.get());
                    assertTrue(observer.executionReport("instance").isPresent());
                }
            };
            activeHandle.set(handle);
            doAnswer(call -> { persisted.set(true); return null; }).when(lifecycle).started(ticket, null);
            submit(observer, ticket, handle);
            verify(lifecycle).started(ticket, null);
            TaskRuntimeReference job = new TaskRuntimeReference("YAKFLOW", "real-job-id");
            reference.complete(Optional.of(job));
            verify(lifecycle, timeout(5000)).runtimeReference(ticket, job);
            doAnswer(call -> {
                instance.setStatus(TaskInstanceStatus.CANCELED);
                return null;
            }).when(lifecycle).complete(ticket, TaskOutcome.CANCELED, "{\"version\":3}");
            completed.complete(TaskOutcome.CANCELED);
            verify(lifecycle, timeout(5000)).complete(ticket, TaskOutcome.CANCELED, "{\"version\":3}");
            verify(lifecycle, never()).uncertain(ticket);
        }
    }

    @Test
    void unconfirmedExecutionPersistsObservationWithoutDeclaringTerminalOutcome() throws Exception {
        try (TaskExecutionObserver observer = new TaskExecutionObserver()) {
            InstanceLifecycleService lifecycle = mock(InstanceLifecycleService.class);
            inject(observer, "lifecycle", lifecycle);
            TaskHandle handle = mock(TaskHandle.class);
            String report = "{\"version\":3,\"tables\":[{\"mappingIndex\":0}]}";
            when(handle.executionReport()).thenReturn(Optional.of(report));
            DispatchTicket ticket = new DispatchTicket("ws", "instance", "task", 1, "DATA_SYNC", "revision", "token", false);
            var terminal = TaskExecutionObserver.class.getDeclaredMethod("observeTerminal", DispatchTicket.class,
                    TaskHandle.class, TaskOutcome.class, Throwable.class);
            terminal.setAccessible(true);
            terminal.invoke(observer, ticket, handle, null, new IllegalStateException("DDL response unknown"));
            verify(lifecycle).uncertain(ticket, report);
            verify(lifecycle, never()).complete(any(), any());
            verify(lifecycle, never()).complete(any(), any(), any());
        }
    }

    private static void submit(TaskExecutionObserver observer, DispatchTicket ticket, TaskHandle handle) throws Exception {
        var method = TaskExecutionObserver.class.getDeclaredMethod("observeSubmission", DispatchTicket.class, TaskHandle.class, Throwable.class);
        method.setAccessible(true);
        method.invoke(observer, ticket, handle, null);
    }

    private static void inject(TaskExecutionObserver observer, String name, Object value) throws Exception {
        var field = TaskExecutionObserver.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(observer, value);
    }
}
