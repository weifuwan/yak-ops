package io.yak.ops.task.execution.dispatch.embedded;

import io.yak.ops.business.task.instance.DispatchTicket;
import io.yak.ops.business.task.instance.InstanceLifecycleService;
import io.yak.ops.business.task.instance.InstanceService;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.plugin.task.api.execution.TaskRuntimeReference;
import io.yak.ops.plugin.task.api.execution.TaskStartRejectedException;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.util.OptionalInt;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Observes submitted physical Jobs and commits their authoritative business outcomes. */
@Component
public class TaskExecutionObserver implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TaskExecutionObserver.class);

    @Resource
    private InstanceLifecycleService lifecycle;

    @Resource
    private InstanceService instances;

    private final ConcurrentHashMap<String, TaskHandle> active = new ConcurrentHashMap<>();
    private final ExecutorService callbacks = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Attaches submission and terminal callbacks to a physically started Task.
     *
     * @param ticket durable launch ownership token
     * @param submission pending physical execution handle
     */
    public void observe(DispatchTicket ticket, CompletionStage<TaskHandle> submission) {
        submission.whenCompleteAsync(
                (handle, failure) -> {
                    try (MDC.MDCCloseable ignored = MDC.putCloseable("taskLogId", ticket.instanceId())) {
                        observeSubmission(ticket, handle, failure);
                    }
                },
                callbacks);
    }

    /**
     * Requests cancellation of one active physical execution.
     *
     * @param instanceId active Task Instance identity
     */
    public void cancel(String instanceId) {
        TaskHandle handle = active.get(instanceId);
        if (handle != null) {
            handle.cancel();
        }
    }

    /** Returns process-local progress for the physical Job currently owned by this Instance. */
    public OptionalInt progressPercent(String instanceId) {
        TaskHandle handle = active.get(instanceId);
        return handle == null ? OptionalInt.empty() : handle.progressPercent();
    }

    /** Returns a process-owned observation only; terminal reads use the persisted Instance report. */
    public java.util.Optional<String> executionReport(String instanceId) {
        TaskHandle handle = active.get(instanceId);
        if (handle == null) return java.util.Optional.empty();
        return handle.executionReport();
    }

    /** Requests cancellation for all active physical executions during process shutdown. */
    public void cancelActive() {
        active.forEach((instanceId, handle) -> {
            try {
                handle.cancel();
            } catch (RuntimeException failure) {
                LOG.warn(
                        "Unable to request Task shutdown cancellation, instanceId={}, exceptionType={}",
                        instanceId,
                        failure.getClass().getSimpleName());
            }
        });
    }

    private void observeSubmission(DispatchTicket ticket, TaskHandle handle, Throwable failure) {
        if (failure != null) {
            Throwable cause = failure;
            if (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            try {
                if (cause instanceof TaskStartRejectedException) {
                    lifecycle.complete(ticket, TaskOutcome.FAILED);
                } else {
                    lifecycle.uncertain(ticket);
                }
            } catch (RuntimeException persistFailure) {
                LOG.error(
                        "Task submission result could not be persisted, instanceId={}, exceptionType={}",
                        ticket.instanceId(),
                        persistFailure.getClass().getSimpleName());
            }
            return;
        }
        String instanceId = ticket.instanceId();
        active.put(instanceId, handle);
        try {
            TaskRuntimeReference reference =
                    handle.runtimeReference().orElse(new TaskRuntimeReference(ticket.taskType(), ticket.instanceId()));
            lifecycle.started(ticket, reference);
            LOG.info("Task Instance Job started, instanceId={}, jobId={}", instanceId, reference.id());
            if (instances.instance(ticket.workspaceId(), instanceId).getStatus() == TaskInstanceStatus.CANCELLING) {
                handle.cancel();
            }
            handle.completion()
                    .whenCompleteAsync(
                            (outcome, error) -> {
                                try (MDC.MDCCloseable ignored = MDC.putCloseable("taskLogId", ticket.instanceId())) {
                                    observeTerminal(ticket, handle, outcome, error);
                                }
                            },
                            callbacks);
        } catch (RuntimeException observationFailure) {
            lifecycle.uncertain(ticket);
            active.remove(instanceId, handle);
            LOG.error(
                    "Unable to observe Task execution, instanceId={}, exceptionType={}",
                    instanceId,
                    observationFailure.getClass().getSimpleName());
        }
    }

    private void observeTerminal(DispatchTicket ticket, TaskHandle handle, TaskOutcome outcome, Throwable failure) {
        try {
            if (failure == null && outcome != null) {
                try {
                    OptionalInt percent = handle.progressPercent();
                    if (percent.isPresent()) {
                        lifecycle.progress(ticket, percent.getAsInt());
                    }
                } catch (RuntimeException metricsFailure) {
                    LOG.warn(
                            "Unable to persist Task progress, instanceId={}, exceptionType={}",
                            ticket.instanceId(),
                            metricsFailure.getClass().getSimpleName());
                }
                var report = handle.executionReport();
                if (report.isPresent()) lifecycle.complete(ticket, outcome, report.get());
                else lifecycle.complete(ticket, outcome);
                LOG.info("Task Instance completed, instanceId={}, outcome={}", ticket.instanceId(), outcome);
            } else {
                lifecycle.uncertain(ticket);
            }
        } catch (RuntimeException persistenceFailure) {
            LOG.error(
                    "Task terminal outcome persistence failed, instanceId={}, exceptionType={}",
                    ticket.instanceId(),
                    persistenceFailure.getClass().getSimpleName());
            try {
                lifecycle.uncertain(ticket);
            } catch (RuntimeException reconciliationFailure) {
                LOG.error(
                        "Unable to protect uncertain Task execution, instanceId={}, exceptionType={}",
                        ticket.instanceId(),
                        reconciliationFailure.getClass().getSimpleName());
            }
        } finally {
            active.remove(ticket.instanceId(), handle);
        }
    }

    @PreDestroy
    @Override
    public void close() {
        callbacks.shutdown();
    }
}
