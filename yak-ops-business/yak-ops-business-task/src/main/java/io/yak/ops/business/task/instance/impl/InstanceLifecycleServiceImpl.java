package io.yak.ops.business.task.instance.impl;

import io.yak.ops.business.task.instance.DispatchTicket;
import io.yak.ops.business.task.instance.InstanceLifecycleService;
import io.yak.ops.business.task.instance.PendingRun;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.task.TaskAttemptStatus;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.task.AttemptEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.task.AttemptRepository;
import io.yak.ops.dao.repository.task.ExecutionClaimRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.plugin.task.api.execution.TaskRuntimeReference;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomic business lifecycle transitions that fence cancellation against dispatch. */
@Service
public class InstanceLifecycleServiceImpl implements InstanceLifecycleService {

    @Resource
    private InstanceRepository instances;

    @Resource
    private AttemptRepository attempts;

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
        List<AttemptEntity> list = attempts.findByInstance(workspaceId, instanceId);
        if (list.size() != 1
                || !attempts.claimPending(workspaceId, list.getFirst().getId(), ownerIncarnation, dispatchToken)) {
            return null;
        }
        return new DispatchTicket(
                workspaceId,
                instanceId,
                list.getFirst().getId(),
                instance.getDefinitionId(),
                instance.getDefinitionVersion(),
                instance.getTaskType(),
                instance.getResourceRevision(),
                dispatchToken);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void started(DispatchTicket ticket, TaskRuntimeReference reference) {
        if (!attempts.markRunning(
                ticket.workspaceId(), ticket.attemptId(), ticket.dispatchToken(), reference.engine(), reference.id())) {
            throw new IllegalStateException("Task Attempt launch ownership changed");
        }
        instances.markRunning(ticket.workspaceId(), ticket.instanceId());
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
        TaskAttemptStatus attemptStatus =
                switch (outcome) {
                    case FINISHED -> TaskAttemptStatus.FINISHED;
                    case FAILED -> TaskAttemptStatus.FAILED;
                    case CANCELED -> TaskAttemptStatus.CANCELED;
                };
        TaskInstanceStatus instanceStatus =
                switch (outcome) {
                    case FINISHED -> TaskInstanceStatus.FINISHED;
                    case FAILED -> TaskInstanceStatus.FAILED;
                    case CANCELED -> TaskInstanceStatus.CANCELED;
                };
        String reason = outcome == TaskOutcome.FAILED ? "RUNTIME_FAILED" : null;
        if (!attempts.markTerminal(
                ticket.workspaceId(), ticket.attemptId(), ticket.dispatchToken(), attemptStatus, reason)) {
            throw new IllegalStateException("Task Attempt terminal update rejected");
        }
        if (!instances.markTerminal(ticket.workspaceId(), ticket.instanceId(), instanceStatus, reason)) {
            throw new IllegalStateException("Task Instance terminal update rejected");
        }
        claims.releaseOwned(ticket.workspaceId(), ticket.instanceId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uncertain(DispatchTicket ticket) {
        instances.findByIdForUpdate(ticket.workspaceId(), ticket.instanceId()).ifPresent(instance -> {
            if (!instance.getStatus().isTerminal()) {
                instances.markReconciling(ticket.workspaceId(), ticket.instanceId());
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskInstanceStatus cancel(String workspaceId, String instanceId) {
        InstanceEntity instance = instances
                .findByIdForUpdate(workspaceId, instanceId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        if (instance.getStatus().isTerminal() || instance.getStatus() == TaskInstanceStatus.RECONCILING) {
            return instance.getStatus();
        }
        List<AttemptEntity> list = attempts.findByInstance(workspaceId, instanceId);
        if (list.size() != 1) {
            throw new IllegalStateException("Task Instance must have exactly one initial Attempt");
        }
        AttemptEntity attempt = list.getFirst();
        if (attempt.getStatus() == TaskAttemptStatus.PENDING && attempts.cancelPending(workspaceId, attempt.getId())) {
            if (!instances.markTerminal(workspaceId, instanceId, TaskInstanceStatus.CANCELED, null)) {
                throw new IllegalStateException("Unable to complete pending cancellation");
            }
            claims.releaseOwned(workspaceId, instanceId);
            return TaskInstanceStatus.CANCELED;
        }
        instances.markCancelling(workspaceId, instanceId);
        return instances.findById(workspaceId, instanceId).orElseThrow().getStatus();
    }

    @Override
    public List<PendingRun> pendingRuns() {
        return attempts.pending().stream()
                .filter(attempt -> instances
                        .findById(attempt.getWorkspaceId(), attempt.getInstanceId())
                        .map(instance -> instance.getStatus() == TaskInstanceStatus.SUBMITTED)
                        .orElse(false))
                .map(attempt -> new PendingRun(attempt.getWorkspaceId(), attempt.getInstanceId()))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reconcilePrevious(String currentOwner) {
        for (AttemptEntity attempt : attempts.activeByOtherOwner(currentOwner)) {
            instances
                    .findByIdForUpdate(attempt.getWorkspaceId(), attempt.getInstanceId())
                    .ifPresent(instance -> {
                        if (!instance.getStatus().isTerminal()) {
                            instances.markReconciling(instance.getWorkspaceId(), instance.getId());
                        }
                    });
        }
    }
}
