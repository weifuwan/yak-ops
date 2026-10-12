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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final ConcurrentHashMap<String, TaskHandle> active = new ConcurrentHashMap<>();

    @PostConstruct
    void reconcile() {
        lifecycle.reconcilePrevious(owner.incarnation());
        for (var pending : lifecycle.pendingRuns()) {
            dispatch(pending.workspaceId(), pending.instanceId());
        }
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
        TaskInstanceStatus result = lifecycle.cancel(workspaceId, instanceId);
        if (result == TaskInstanceStatus.CANCELLING) {
            TaskHandle handle = active.get(instanceId);
            if (handle != null) {
                handle.cancel();
            }
        }
        return result;
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
            if (lifecycle.instance(workspaceId, instanceId).getStatus() == TaskInstanceStatus.CANCELLING) {
                lifecycle.complete(ticket, TaskOutcome.CANCELED);
                return;
            }
            DispatchTicket launched = ticket;
            physicalStartInvoked = true;
            executable.start(parameters, plan, context).whenComplete((handle, error) -> {
                if (error != null) {
                    Throwable cause =
                            error instanceof java.util.concurrent.CompletionException ? error.getCause() : error;
                    if (cause instanceof TaskStartRejectedException) {
                        lifecycle.complete(launched, TaskOutcome.FAILED);
                    } else {
                        lifecycle.uncertain(launched);
                    }
                    return;
                }
                active.put(instanceId, handle);
                try {
                    TaskRuntimeReference reference = handle.runtimeReference()
                            .orElse(new TaskRuntimeReference(launched.taskType(), launched.attemptId()));
                    lifecycle.started(launched, reference);
                    if (lifecycle.instance(workspaceId, instanceId).getStatus() == TaskInstanceStatus.CANCELLING) {
                        handle.cancel();
                    }
                    handle.completion().whenComplete((outcome, failure) -> {
                        try {
                            if (failure == null) {
                                lifecycle.complete(launched, outcome);
                            } else {
                                lifecycle.uncertain(launched);
                            }
                        } finally {
                            active.remove(instanceId, handle);
                        }
                    });
                } catch (RuntimeException failure) {
                    lifecycle.uncertain(launched);
                    active.remove(instanceId, handle);
                    LOG.error(
                            "Unable to observe Task execution, instanceId={}, exceptionType={}",
                            instanceId,
                            failure.getClass().getSimpleName());
                }
            });
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

    private ExecutionResource resource(ExecutionClaimEntity claim) {
        return new ExecutionResource(claim.getResourceType(), claim.getResourceKey());
    }

    @PreDestroy
    @Override
    public void close() {
        workers.shutdown();
    }
}
