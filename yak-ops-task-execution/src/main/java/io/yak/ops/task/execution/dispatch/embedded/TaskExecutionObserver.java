package io.yak.ops.task.execution.dispatch.embedded;

import io.yak.ops.business.task.instance.DispatchTicket;
import io.yak.ops.business.task.instance.InstanceLifecycleService;
import io.yak.ops.business.task.instance.InstanceService;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.plugin.task.api.execution.TaskRuntimeReference;
import io.yak.ops.plugin.task.api.execution.TaskStartRejectedException;
import io.yak.ops.worker.executor.PhysicalTaskEngineDelegator;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
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

    @Resource
    private PhysicalTaskEngineDelegator worker;

    private final ExecutorService callbacks = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Attaches submission and terminal callbacks to a physically started Task.
     *
     * @param ticket durable launch ownership token
     * @param submission pending physical execution handle
     */
    public void observe(DispatchTicket ticket, CompletionStage<TaskHandle> submission) {
        observeTracked(ticket, submission);
    }

    /** Completes only after terminal business persistence has been attempted. */
    public CompletionStage<TaskOutcome> observeTracked(DispatchTicket ticket, CompletionStage<TaskHandle> submission) {
        CompletableFuture<TaskOutcome> observed = new CompletableFuture<>();
        submission.whenCompleteAsync(
                (handle, failure) -> {
                    try (MDC.MDCCloseable ignored = MDC.putCloseable("taskLogId", ticket.instanceId())) {
                        observeSubmission(ticket, handle, failure, observed);
                    } catch (RuntimeException error) {
                        observed.completeExceptionally(error);
                    }
                },
                callbacks);
        return observed;
    }

    /**
     * Requests cancellation of one active physical execution.
     *
     * @param instanceId active Task Instance identity
     */
    public void cancel(String instanceId) {
        TaskHandle handle = worker.taskHandle(instanceId).orElse(null);
        if (handle != null) {
            handle.cancel();
        }
    }

    /** Returns process-local progress for the physical Job currently owned by this Instance. */
    public OptionalInt progressPercent(String instanceId) {
        TaskHandle handle = worker.taskHandle(instanceId).orElse(null);
        return handle == null ? OptionalInt.empty() : handle.progressPercent();
    }

    /** Returns a process-owned observation only; terminal reads use the persisted Instance report. */
    public java.util.Optional<String> executionReport(String instanceId) {
        TaskHandle handle = worker.taskHandle(instanceId).orElse(null);
        if (handle == null) return java.util.Optional.empty();
        return publicObservation(instanceId, handle, false);
    }

    /** Returns a compact observation for an owned active Instance without table detail. */
    public Optional<String> executionSummary(String instanceId) {
        TaskHandle handle = worker.taskHandle(instanceId).orElse(null);
        if (handle == null) return Optional.empty();
        return publicObservation(instanceId, handle, true);
    }

    private Optional<String> publicObservation(String instanceId, TaskHandle handle, boolean summary) {
        try {
            var report = summary ? handle.executionSummary() : handle.executionReport();
            int limit = summary ? 16384 : 262144;
            if (report.isEmpty()) return Optional.empty();
            if (report.get().length() > limit
                    || !JSONUtils.readTree(report.get()).isObject()) {
                throw new IllegalArgumentException("Invalid public Task execution observation");
            }
            return report;
        } catch (RuntimeException failure) {
            LOG.warn(
                    "Task observation is unavailable, instanceId={}, exceptionType={}",
                    instanceId,
                    failure.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Requests cancellation for all active physical executions during process shutdown. */
    public void cancelActive() {
        worker.queryTaskExecutors().forEach(executor -> {
            String instanceId = executor.getId();
            try {
                executor.kill();
            } catch (RuntimeException failure) {
                LOG.warn(
                        "Unable to request Task shutdown cancellation, instanceId={}, exceptionType={}",
                        instanceId,
                        failure.getClass().getSimpleName());
            }
        });
    }

    private void observeSubmission(DispatchTicket ticket, TaskHandle handle, Throwable failure) {
        observeSubmission(ticket, handle, failure, null);
    }

    private void observeSubmission(
            DispatchTicket ticket, TaskHandle handle, Throwable failure, CompletableFuture<TaskOutcome> observed) {
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
                if (observed != null) observed.completeExceptionally(persistFailure);
                return;
            }
            if (observed != null) {
                if (cause instanceof TaskStartRejectedException) observed.complete(TaskOutcome.FAILED);
                else observed.completeExceptionally(cause);
            }
            return;
        }
        String instanceId = ticket.instanceId();
        try {
            worker.registerTaskHandle(instanceId, handle);
            TaskRuntimeReference reference = handle.runtimeReference().orElse(null);
            lifecycle.started(ticket, reference);
            LOG.info("Task Instance started, instanceId={}", instanceId);
            handle.runtimeReferenceReady()
                    .whenCompleteAsync(
                            (known, referenceFailure) -> {
                                try (MDC.MDCCloseable ignored = MDC.putCloseable("taskLogId", instanceId)) {
                                    if (referenceFailure == null && known != null && known.isPresent()) {
                                        try {
                                            lifecycle.runtimeReference(ticket, known.orElseThrow());
                                        } catch (RuntimeException persistenceFailure) {
                                            LOG.warn(
                                                    "Task runtime identity is awaiting persistence, instanceId={}, exceptionType={}",
                                                    instanceId,
                                                    persistenceFailure
                                                            .getClass()
                                                            .getSimpleName());
                                        }
                                    }
                                }
                            },
                            callbacks);
            if (instances.instance(ticket.workspaceId(), instanceId).getStatus() == TaskInstanceStatus.CANCELLING) {
                handle.cancel();
            }
            handle.completion()
                    .whenCompleteAsync(
                            (outcome, error) -> {
                                try (MDC.MDCCloseable ignored = MDC.putCloseable("taskLogId", ticket.instanceId())) {
                                    boolean persisted = observeTerminal(ticket, handle, outcome, error);
                                    if (observed != null) {
                                        if (!persisted)
                                            observed.completeExceptionally(
                                                    new IllegalStateException("Task outcome could not be persisted"));
                                        else if (error == null && outcome != null) observed.complete(outcome);
                                        else
                                            observed.completeExceptionally(
                                                    error == null
                                                            ? new IllegalStateException("Unknown Task outcome")
                                                            : error);
                                    }
                                }
                            },
                            callbacks);
            handle.start();
        } catch (RuntimeException observationFailure) {
            try {
                handle.cancel();
            } catch (RuntimeException cancellationFailure) {
                observationFailure.addSuppressed(cancellationFailure);
            }
            lifecycle.uncertain(ticket);
            LOG.error(
                    "Unable to observe Task execution, instanceId={}, exceptionType={}",
                    instanceId,
                    observationFailure.getClass().getSimpleName());
            if (observed != null) observed.completeExceptionally(observationFailure);
        }
    }

    private boolean observeTerminal(DispatchTicket ticket, TaskHandle handle, TaskOutcome outcome, Throwable failure) {
        boolean persisted = false;
        try {
            if (failure == null && outcome != null) {
                handle.runtimeReference().ifPresent(reference -> lifecycle.runtimeReference(ticket, reference));
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
                var report = publicObservation(ticket.instanceId(), handle, false);
                if (report.isPresent()) lifecycle.complete(ticket, outcome, report.get());
                else lifecycle.complete(ticket, outcome);
                LOG.info("Task Instance completed, instanceId={}, outcome={}", ticket.instanceId(), outcome);
                // complete() is intentionally idempotent. Never ACK a stale
                // dispatch or a terminal outcome skipped due to RECONCILING.
                TaskInstanceStatus expectedStatus =
                        switch (outcome) {
                            case FINISHED -> TaskInstanceStatus.FINISHED;
                            case FAILED -> TaskInstanceStatus.FAILED;
                            case CANCELED -> TaskInstanceStatus.CANCELED;
                        };
                io.yak.ops.dao.entity.task.InstanceEntity actual =
                        instances.instance(ticket.workspaceId(), ticket.instanceId());
                persisted = actual != null
                        && actual.getStatus() == expectedStatus
                        && ticket.dispatchToken().equals(actual.getDispatchToken());
            } else {
                var report = publicObservation(ticket.instanceId(), handle, false);
                if (report.isPresent()) lifecycle.uncertain(ticket, report.get());
                else lifecycle.uncertain(ticket);
                persisted = true;
            }
        } catch (RuntimeException persistenceFailure) {
            LOG.error(
                    "Task terminal outcome persistence failed, instanceId={}, exceptionType={}",
                    ticket.instanceId(),
                    persistenceFailure.getClass().getSimpleName());
            try {
                lifecycle.uncertain(ticket);
                // Reconciling is not a confirmed physical SUCCESS/FAILED ACK.
                persisted = false;
            } catch (RuntimeException reconciliationFailure) {
                LOG.error(
                        "Unable to protect uncertain Task execution, instanceId={}, exceptionType={}",
                        ticket.instanceId(),
                        reconciliationFailure.getClass().getSimpleName());
            }
        }
        return persisted;
    }

    @PreDestroy
    @Override
    public void close() {
        callbacks.shutdown();
    }
}
