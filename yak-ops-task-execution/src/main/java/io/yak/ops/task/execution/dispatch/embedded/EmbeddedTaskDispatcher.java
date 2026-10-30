package io.yak.ops.task.execution.dispatch.embedded;

import io.yak.ops.business.task.instance.DispatchTicket;
import io.yak.ops.business.task.instance.InstanceLifecycleService;
import io.yak.ops.business.task.instance.InstanceService;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.entity.task.TaskGroupQueueEntity;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.dao.repository.task.TaskGroupQueueRepository;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.task.execution.dispatch.TaskExecutionCoordinator;
import io.yak.ops.task.execution.owner.EmbeddedExecutionOwner;
import io.yak.ops.worker.executor.PhysicalTask;
import io.yak.ops.worker.executor.PhysicalTaskEngineDelegator;
import io.yak.ops.worker.task.executor.TaskExecutionContext;
import io.yak.ops.worker.task.executor.exceptions.TaskExecutorRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Executes already-admitted Tasks on the verified single embedded execution owner. */
@Component
public class EmbeddedTaskDispatcher implements TaskExecutionCoordinator, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedTaskDispatcher.class);

    @Resource
    private EmbeddedExecutionOwner owner;

    @Resource
    private InstanceLifecycleService lifecycle;

    @Resource
    private InstanceService instances;

    @Resource
    private TaskExecutionObserver observer;

    @Resource
    private TaskPluginRegistry registry;

    @Resource
    private DefinitionVersionRepository definitions;

    @Resource
    private TaskGroupQueueRepository claims;

    @Resource
    private PhysicalTaskEngineDelegator worker;

    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("task-dispatch-reconcile", 0).factory());
    private final Object[] launchFences = new Object[256];

    public EmbeddedTaskDispatcher() {
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
        for (io.yak.ops.business.task.instance.PendingRun pending : lifecycle.pendingRuns()) {
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
    @Override
    public void dispatch(String workspaceId, String instanceId) {
        owner.incarnation();
        // A queued Task cannot consume a Worker slot or claim its Instance until
        // the Master has atomically acquired every required TaskGroup slot.
        if (!claims.allAcquired(workspaceId, instanceId)) {
            return;
        }
        try {
            worker.dispatch(workspaceId, instanceId, registry, this::preparePhysicalTask);
        } catch (TaskExecutorRuntimeException busy) {
            // No physical slot was obtained. The durable SUBMITTED row stays pending.
            LOG.debug("All Worker slots are busy; Task remains pending, instanceId={}", instanceId);
        } catch (IllegalArgumentException duplicate) {
            LOG.debug("Task is already registered with the Worker, instanceId={}", instanceId);
        }
    }

    @Override
    public TaskInstanceStatus cancel(String workspaceId, String instanceId) {
        synchronized (launchFence(instanceId)) {
            TaskInstanceStatus result = lifecycle.cancel(workspaceId, instanceId);
            if (result == TaskInstanceStatus.CANCELLING) {
                try {
                    worker.kill(instanceId);
                } catch (IllegalArgumentException notRunning) {
                    // Cancellation remains durable; preparePhysicalTask observes CANCELLING.
                }
            }
            return result;
        }
    }

    /**
     * Master-side durable admission. The physical plugin is initialized and triggered
     * by PhysicalTaskExecutor; this method never starts a TaskHandle.
     */
    private PhysicalTask preparePhysicalTask(
            TaskExecutionContext taskContext, Supplier<ExecutableTaskPlugin> taskPluginSupplier) {
        String workspaceId = taskContext.getWorkspaceId();
        String instanceId = taskContext.getTaskInstanceId();
        DispatchTicket ticket = null;
        try (MDC.MDCCloseable ignored = MDC.putCloseable("taskLogId", instanceId)) {
            ticket = lifecycle.claim(
                    workspaceId,
                    instanceId,
                    owner.incarnation(),
                    UUID.randomUUID().toString());
            if (ticket == null) {
                return completedPhysicalTask(TaskOutcome.CANCELED);
            }
            TaskExecutionIdentity identity = new TaskExecutionIdentity(
                    ticket.workspaceId(), ticket.definitionId(), ticket.definitionVersion(), ticket.instanceId());
            taskContext.setTaskType(ticket.taskType());
            taskContext.setTaskName(ticket.definitionId());

            ExecutableTaskPlugin executable = taskPluginSupplier.get();
            if (executable == null) {
                lifecycle.complete(ticket, TaskOutcome.FAILED);
                return completedPhysicalTask(TaskOutcome.FAILED);
            }

            DefinitionVersionEntity version = definitions
                    .queryCurrent(workspaceId, ticket.definitionId(), ticket.definitionVersion())
                    .orElseThrow(() -> new IllegalStateException("Pinned Task version is not available"));
            ReadonlyConfig parameters = executable.parse(version.getParameterSchemaVersion(), version.getParameters());
            TaskExecutionResources plan = executable.plan(parameters, identity);
            Set<ExecutionResource> owned = claims.findByInstance(workspaceId, instanceId).stream()
                    .map(this::resource)
                    .collect(java.util.stream.Collectors.toSet());
            if (!Objects.equals(plan.resourceRevision(), ticket.resourceRevision())
                    || !owned.equals(Set.copyOf(plan.exclusiveResources()))) {
                lifecycle.complete(ticket, TaskOutcome.FAILED);
                return completedPhysicalTask(TaskOutcome.FAILED);
            }

            DispatchTicket launched = ticket;
            return new PhysicalTask() {
                @Override
                public CompletionStage<TaskOutcome> handle() {
                    synchronized (launchFence(instanceId)) {
                        if (instances.instance(workspaceId, instanceId).getStatus() == TaskInstanceStatus.CANCELLING) {
                            lifecycle.complete(launched, TaskOutcome.CANCELED);
                            return CompletableFuture.completedFuture(TaskOutcome.CANCELED);
                        }
                        try {
                            CompletionStage<TaskHandle> submission =
                                    executable.start(parameters, plan, identity, launched.debug());
                            return observer.observeTracked(launched, submission);
                        } catch (RuntimeException unknown) {
                            try {
                                lifecycle.uncertain(launched);
                            } catch (RuntimeException persistFailure) {
                                unknown.addSuppressed(persistFailure);
                            }
                            return CompletableFuture.failedFuture(unknown);
                        }
                    }
                }

                @Override
                public void cancel() {
                    observer.cancel(instanceId);
                }
            };
        } catch (RuntimeException failure) {
            if (ticket != null) {
                try {
                    lifecycle.complete(ticket, TaskOutcome.FAILED);
                } catch (RuntimeException persistFailure) {
                    failure.addSuppressed(persistFailure);
                    LOG.error("Task admission state could not be persisted, instanceId={}", instanceId, failure);
                    return uncertainPhysicalTask(failure);
                }
            }
            LOG.error("Task plugin admission failed, instanceId={}", instanceId, failure);
            return completedPhysicalTask(TaskOutcome.FAILED);
        }
    }

    private static PhysicalTask completedPhysicalTask(TaskOutcome outcome) {
        return new PhysicalTask() {
            @Override
            public CompletionStage<TaskOutcome> handle() {
                return CompletableFuture.completedFuture(outcome);
            }

            @Override
            public void cancel() {}
        };
    }

    private static PhysicalTask uncertainPhysicalTask(RuntimeException failure) {
        return new PhysicalTask() {
            @Override
            public CompletionStage<TaskOutcome> handle() {
                return CompletableFuture.failedFuture(failure);
            }

            @Override
            public void cancel() {}
        };
    }

    private ExecutionResource resource(TaskGroupQueueEntity claim) {
        return new ExecutionResource(claim.getResourceType(), claim.getResourceKey());
    }

    @PreDestroy
    @Override
    public void close() {
        poller.shutdownNow();
        observer.cancelActive();
    }
}
