package io.yak.ops.business.task.instance.impl;

import io.yak.ops.business.task.instance.DispatchTicket;
import io.yak.ops.business.task.instance.InstanceLifecycleService;
import io.yak.ops.business.task.instance.PendingRun;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.task.ExecutionClaimRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.plugin.task.api.execution.TaskRuntimeReference;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns single-submission dispatch arbitration, cancellation and durable Task Instance outcomes. */
@Service
public class InstanceLifecycleServiceImpl implements InstanceLifecycleService {

    @Resource
    private InstanceRepository instances;

    @Resource
    private ExecutionClaimRepository claims;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DispatchTicket claim(String workspaceId, String instanceId, String ownerIncarnation, String dispatchToken) {
        InstanceEntity instance =
                instances.findByIdForUpdate(workspaceId, instanceId).orElse(null);
        if (instance == null || instance.getStatus() != TaskInstanceStatus.SUBMITTED) {
            return null;
        }
        if (!instances.claim(workspaceId, instanceId, ownerIncarnation, dispatchToken)) {
            return null;
        }
        return new DispatchTicket(
                workspaceId,
                instanceId,
                instance.getDefinitionId(),
                instance.getDefinitionVersion(),
                instance.getTaskType(),
                instance.getResourceRevision(),
                dispatchToken,
                instance.isDebug());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void started(DispatchTicket ticket, TaskRuntimeReference reference) {
        if (!instances.markRunning(
                ticket.workspaceId(),
                ticket.instanceId(),
                ticket.dispatchToken(),
                reference.engine(),
                reference.id())) {
            throw new IllegalStateException("Task Instance launch ownership changed");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(DispatchTicket ticket, TaskOutcome outcome) {
        InstanceEntity instance = instances
                .findByIdForUpdate(ticket.workspaceId(), ticket.instanceId())
                .orElseThrow(() -> new IllegalStateException("Task Instance is missing"));
        if (instance.getStatus().isTerminal() || instance.getStatus() == TaskInstanceStatus.RECONCILING) {
            return;
        }
        TaskInstanceStatus status =
                switch (outcome) {
                    case FINISHED -> TaskInstanceStatus.FINISHED;
                    case FAILED -> TaskInstanceStatus.FAILED;
                    case CANCELED -> TaskInstanceStatus.CANCELED;
                };
        String reason = outcome == TaskOutcome.FAILED ? "RUNTIME_FAILED" : null;
        if (!instances.markTerminal(
                ticket.workspaceId(), ticket.instanceId(), ticket.dispatchToken(), status, reason)) {
            throw new IllegalStateException("Task Instance terminal update rejected");
        }
        claims.releaseOwned(ticket.workspaceId(), ticket.instanceId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uncertain(DispatchTicket ticket) {
        instances.findByIdForUpdate(ticket.workspaceId(), ticket.instanceId()).ifPresent(instance -> {
            if (!instance.getStatus().isTerminal()) {
                instances.markReconciling(ticket.workspaceId(), ticket.instanceId(), ticket.dispatchToken());
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskInstanceStatus cancel(String workspaceId, String instanceId) {
        InstanceEntity instance = instances
                .findByIdForUpdate(workspaceId, instanceId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        TaskInstanceStatus status = instance.getStatus();
        if (status.isTerminal()
                || status == TaskInstanceStatus.RECONCILING
                || status == TaskInstanceStatus.CANCELLING) {
            return status;
        }
        if (status == TaskInstanceStatus.SUBMITTED) {
            if (!instances.cancelSubmitted(workspaceId, instanceId)) {
                throw new IllegalStateException("Task Instance cancellation rejected");
            }
            claims.releaseOwned(workspaceId, instanceId);
            return TaskInstanceStatus.CANCELED;
        }
        if (!instances.markCancelling(workspaceId, instanceId)) {
            throw new IllegalStateException("Task Instance cancellation rejected");
        }
        return TaskInstanceStatus.CANCELLING;
    }

    @Override
    public List<PendingRun> pendingRuns() {
        return instances.pending().stream()
                .map(instance -> new PendingRun(instance.getWorkspaceId(), instance.getId()))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reconcilePrevious(String currentOwner) {
        for (InstanceEntity instance : instances.activeByOtherOwner(currentOwner)) {
            instances
                    .findByIdForUpdate(instance.getWorkspaceId(), instance.getId())
                    .ifPresent(locked -> {
                        if (!locked.getStatus().isTerminal()) {
                            instances.markReconciling(
                                    locked.getWorkspaceId(), locked.getId(), locked.getDispatchToken());
                        }
                    });
        }
    }
}
