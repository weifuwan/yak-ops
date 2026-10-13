package io.yak.ops.task.execution.dispatch.embedded;

import io.yak.ops.business.task.instance.DispatchTicket;
import io.yak.ops.business.task.instance.InstanceLifecycleService;
import io.yak.ops.business.task.instance.InstanceService;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.dao.entity.task.ExecutionClaimEntity;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.dao.repository.task.ExecutionClaimRepository;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.task.execution.dispatch.TaskExecutionCoordinator;
import io.yak.ops.task.execution.owner.EmbeddedExecutionOwner;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
    private ExecutionClaimRepository claims;

    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
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
    @Override
    public void dispatch(String workspaceId, String instanceId) {
        owner.incarnation();
        workers.execute(() -> {
            try (MDC.MDCCloseable ignored = MDC.putCloseable("taskLogId", instanceId)) {
                launch(workspaceId, instanceId);
            }
        });
    }

    /**
     * Records cancellation intent and requests physical cancellation where a handle exists.
     *
     * @param workspaceId authorized Workspace
     * @param instanceId Task Instance to cancel
     * @return current persisted business status
     */
    @Override
    public TaskInstanceStatus cancel(String workspaceId, String instanceId) {
        synchronized (launchFence(instanceId)) {
            TaskInstanceStatus result = lifecycle.cancel(workspaceId, instanceId);
            if (result == TaskInstanceStatus.CANCELLING) {
                observer.cancel(instanceId);
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
            LOG.info("Task Instance launch claimed, instanceId={}", instanceId);
            TaskExecutionIdentity context = new TaskExecutionIdentity(
                    ticket.workspaceId(), ticket.definitionId(), ticket.definitionVersion(), ticket.instanceId());
            TaskPlugin plugin = registry.get(ticket.taskType());
            if (!(plugin instanceof ExecutableTaskPlugin executable)) {
                lifecycle.complete(ticket, TaskOutcome.FAILED);
                return;
            }
            var version = definitions
                    .queryCurrent(workspaceId, ticket.definitionId(), ticket.definitionVersion())
                    .orElseThrow(() -> new IllegalStateException("Pinned Task version is not available"));
            ReadonlyConfig parameters = executable.parse(version.getParameters());
            TaskAdmissionPlan plan = executable.plan(parameters, context);
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
                if (instances.instance(workspaceId, instanceId).getStatus() == TaskInstanceStatus.CANCELLING) {
                    lifecycle.complete(ticket, TaskOutcome.CANCELED);
                    return;
                }
                physicalStartInvoked = true;
                submitted = executable.start(parameters, plan, context);
            }
            // Runtime completion may run on an interrupted YakFlow Job thread. Business
            // transactions must execute on a fresh thread to keep JDBC connections usable.
            observer.observe(launched, submitted);
        } catch (RuntimeException failure) {
            if (ticket != null) {
                if (physicalStartInvoked) {
                    lifecycle.uncertain(ticket);
                } else {
                    lifecycle.complete(ticket, TaskOutcome.FAILED);
                }
            }
            LOG.error("Task dispatch failed, instanceId={}", instanceId, failure);
        }
    }

    private ExecutionResource resource(ExecutionClaimEntity claim) {
        return new ExecutionResource(claim.getResourceType(), claim.getResourceKey());
    }

    @PreDestroy
    @Override
    public void close() {
        poller.shutdownNow();
        observer.cancelActive();
        workers.shutdown();
    }
}
