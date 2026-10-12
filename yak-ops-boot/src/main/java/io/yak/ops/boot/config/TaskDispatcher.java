package io.yak.ops.boot.config;

import io.yak.ops.business.task.execution.DispatchTicket;
import io.yak.ops.business.task.execution.ExecutionLifecycleService;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.dao.entity.task.ExecutionClaimEntity;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.dao.repository.task.ExecutionClaimRepository;
import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionContext;
import io.yak.ops.plugin.task.api.execution.TaskExecutionPlan;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.plugin.task.api.execution.TaskRuntimeReference;
import io.yak.ops.plugin.task.api.execution.TaskStartRejectedException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Executes already-admitted Tasks on the verified single embedded execution owner. */
@Component
public class TaskDispatcher implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TaskDispatcher.class);

    @Resource
    private EmbeddedExecutionOwner owner;

    @Resource
    private ExecutionLifecycleService lifecycle;

    @Resource
    private TaskPluginRegistry registry;

    @Resource
    private DefinitionVersionRepository definitions;

    @Resource
    private ExecutionClaimRepository claims;

    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("task-dispatch-reconcile", 0).factory());
    private final ConcurrentHashMap<String, TaskHandle> active = new ConcurrentHashMap<>();
    private final Object[] launchFences = new Object[256];

    public TaskDispatcher() {
        Arrays.setAll(launchFences, index -> new Object());
    }

    @PostConstruct
    void reconcile() {
        lifecycle.reconcilePrevious(owner.incarnation());
        scanPending();
        poller.scheduleWithFixedDelay(this::scanSafely, 2, 3, TimeUnit.SECONDS);
    }

    private void scanSafely() {
        try {
            scanPending();
        } catch (RuntimeException exception) {
            LOG.error(
                    "Unable to scan pending Task submissions, exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private void scanPending() {
        owner.incarnation();
        for (var pending : lifecycle.pendingRuns()) {
            dispatch(pending.workspaceId(), pending.instanceId());
        }
    }

    private Object launchFence(String instanceId) {
        return launchFences[Math.floorMod(instanceId.hashCode(), launchFences.length)];
    }

    /**
     * Dispatches one committed submission on a background worker.
     *
     * @param workspaceId trusted Workspace captured by the HTTP admission boundary
     * @param instanceId durably admitted Task Instance
     */
    public void dispatch(String workspaceId, String instanceId) {
        owner.incarnation();
        workers.execute(() -> launch(workspaceId, instanceId));
    }

    /**
     * Records cancellation intent and requests physical cancellation where a handle exists.
     *
     * @param workspaceId authorized Workspace
     * @param instanceId Task Instance to cancel
     * @return current persisted business status
     */
    public TaskInstanceStatus cancel(String workspaceId, String instanceId) {
        synchronized (launchFence(instanceId)) {
            TaskInstanceStatus result = lifecycle.cancel(workspaceId, instanceId);
            if (result == TaskInstanceStatus.CANCELLING) {
                TaskHandle handle = active.get(instanceId);
                if (handle != null) {
                    handle.cancel();
                }
            }
            return result;
        }
    }

    private void launch(String workspaceId, String instanceId) {
        DispatchTicket ticket = null;
        boolean physicalStartInvoked = false;
        try {
            String token = UUID.randomUUID().toString();
            ticket = lifecycle.claim(workspaceId, instanceId, owner.incarnation(), token);
            if (ticket == null) {
                return;
            }
            TaskExecutionContext context = new TaskExecutionContext(
                    ticket.workspaceId(),
                    ticket.definitionId(),
                    ticket.definitionVersion(),
                    ticket.instanceId(),
                    ticket.attemptId());
            TaskPlugin plugin = registry.get(ticket.taskType());
            if (!(plugin instanceof ExecutableTaskPlugin executable)) {
                lifecycle.complete(ticket, TaskOutcome.FAILED);
                return;
            }
            var version = definitions
                    .queryCurrent(workspaceId, ticket.definitionId(), ticket.definitionVersion())
                    .orElseThrow(() -> new IllegalStateException("Pinned Task version is not available"));
            TaskParameters parameters = executable.parse(version.getParameters());
            TaskExecutionPlan plan = executable.plan(parameters, context);
            Set<ExecutionResource> owned = claims.findByInstance(workspaceId, instanceId).stream()
                    .map(this::resource)
                    .collect(java.util.stream.Collectors.toSet());
            if (!Objects.equals(plan.resourceRevision(), ticket.resourceRevision())
                    || !owned.equals(Set.copyOf(plan.exclusiveResources()))) {
                lifecycle.complete(ticket, TaskOutcome.FAILED);
                return;
            }
            DispatchTicket launched = ticket;
            CompletionStage<TaskHandle> submitted;
            synchronized (launchFence(instanceId)) {
                if (lifecycle.instance(workspaceId, instanceId).getStatus() == TaskInstanceStatus.CANCELLING) {
                    lifecycle.complete(ticket, TaskOutcome.CANCELED);
                    return;
                }
                physicalStartInvoked = true;
                submitted = executable.start(parameters, plan, context);
            }
            // Runtime completion may run on an interrupted YakFlow Job thread. Business
            // transactions must execute on a fresh thread to keep JDBC connections usable.
            submitted.whenCompleteAsync((handle, error) -> observeSubmission(launched, handle, error), workers);
        } catch (RuntimeException failure) {
            if (ticket != null) {
                if (physicalStartInvoked) {
                    lifecycle.uncertain(ticket);
                } else {
                    lifecycle.complete(ticket, TaskOutcome.FAILED);
                }
            }
            LOG.error(
                    "Task dispatch failed, instanceId={}, exceptionType={}",
                    instanceId,
                    failure.getClass().getSimpleName());
        }
    }

    private void observeSubmission(DispatchTicket ticket, TaskHandle handle, Throwable failure) {
        if (failure != null) {
            Throwable cause = failure;
            if (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
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
                    handle.runtimeReference().orElse(new TaskRuntimeReference(ticket.taskType(), ticket.attemptId()));
            lifecycle.started(ticket, reference);
            if (lifecycle.instance(ticket.workspaceId(), instanceId).getStatus() == TaskInstanceStatus.CANCELLING) {
                handle.cancel();
            }
            handle.completion()
                    .whenCompleteAsync((outcome, error) -> observeTerminal(ticket, handle, outcome, error), workers);
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
                lifecycle.complete(ticket, outcome);
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

    private ExecutionResource resource(ExecutionClaimEntity claim) {
        return new ExecutionResource(claim.getResourceType(), claim.getResourceKey());
    }

    @PreDestroy
    @Override
    public void close() {
        poller.shutdownNow();
        active.forEach((instanceId, handle) -> {
            try {
                handle.cancel();
            } catch (RuntimeException exception) {
                LOG.warn(
                        "Unable to request Task shutdown cancellation, instanceId={}, exceptionType={}",
                        instanceId,
                        exception.getClass().getSimpleName());
            }
        });
        workers.shutdown();
    }
}
