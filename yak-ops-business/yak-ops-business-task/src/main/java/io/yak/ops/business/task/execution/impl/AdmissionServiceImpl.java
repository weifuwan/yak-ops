package io.yak.ops.business.task.execution.impl;

import io.yak.ops.business.task.execution.AdmissionRequest;
import io.yak.ops.business.task.execution.AdmissionService;
import io.yak.ops.business.task.execution.SubmissionResult;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.enums.task.TaskAttemptStatus;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.task.AttemptEntity;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.ExecutionClaimEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.task.AttemptRepository;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.dao.repository.task.ExecutionClaimRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionContext;
import io.yak.ops.plugin.task.api.execution.TaskExecutionPlan;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomically pins a published Definition, persists one Attempt, and acquires all
 * declared resources. Physical execution must occur after this transaction commits.
 */
@Service
public class AdmissionServiceImpl implements AdmissionService {
    @Resource
    private DefinitionRepository definitions;

    @Resource
    private DefinitionVersionRepository versions;

    @Resource
    private InstanceRepository instances;

    @Resource
    private AttemptRepository attempts;

    @Resource
    private ExecutionClaimRepository claims;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmissionResult admit(AdmissionRequest request) {
        TaskExecutionContext context = request.context();
        String workspaceId = context.workspaceId();
        if (!workspaceId.equals(WorkspaceContext.requireWorkspaceId())) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        var replay = instances.findByRequest(workspaceId, TaskTriggerType.MANUAL, request.requestId());
        if (replay.isPresent()) {
            InstanceEntity existing = replay.orElseThrow();
            if (!existing.getRequestFingerprint().equals(request.requestFingerprint())) {
                throw new BusinessException(CommonErrorCode.RESOURCE_DUPLICATION);
            }
            List<AttemptEntity> previous = attempts.findByInstance(workspaceId, existing.getId());
            if (previous.isEmpty()) {
                throw new IllegalStateException("Task Instance has no persisted Attempt");
            }
            return new SubmissionResult(
                    existing.getId(),
                    previous.getFirst().getId(),
                    existing.getDefinitionVersion(),
                    existing.getStatus(),
                    true);
        }

        DefinitionEntity definition = definitions
                .queryByIdForUpdate(workspaceId, context.definitionId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        if (definition.getStatus() != DefinitionStatus.PUBLISHED
                || !definition.getCurrentVersion().equals(context.definitionVersion())
                || !definition.getTaskType().equals(request.plugin().type())
                || versions.queryCurrent(workspaceId, definition.getId(), context.definitionVersion())
                        .isEmpty()) {
            throw new BusinessException(CommonErrorCode.RESOURCE_DUPLICATION);
        }

        TaskExecutionPlan currentPlan = request.plugin().plan(request.parameters(), context);
        if (!request.plan().equals(currentPlan)) {
            throw new BusinessException(CommonErrorCode.RESOURCE_DUPLICATION);
        }

        InstanceEntity instance = new InstanceEntity();
        instance.setId(context.instanceId());
        instance.setWorkspaceId(workspaceId);
        instance.setDefinitionId(context.definitionId());
        instance.setDefinitionVersion(context.definitionVersion());
        instance.setTaskType(definition.getTaskType());
        instance.setTriggerType(TaskTriggerType.MANUAL);
        instance.setRequestId(request.requestId());
        instance.setRequestFingerprint(request.requestFingerprint());
        instance.setResourceRevision(currentPlan.resourceRevision());
        instance.setStatus(TaskInstanceStatus.SUBMITTED);
        instance.setStateVersion(0);
        instance.initCreate(request.operatorId());
        instances.add(instance);

        AttemptEntity attempt = new AttemptEntity();
        attempt.setId(context.attemptId());
        attempt.setWorkspaceId(workspaceId);
        attempt.setInstanceId(context.instanceId());
        attempt.setAttemptNumber(1);
        attempt.setStatus(TaskAttemptStatus.PENDING);
        attempt.initCreate(request.operatorId());
        attempts.add(attempt);

        for (ExecutionResource resource : currentPlan.exclusiveResources()) {
            ExecutionClaimEntity claim = new ExecutionClaimEntity();
            claim.setWorkspaceId(workspaceId);
            claim.setResourceType(resource.type());
            claim.setResourceKey(resource.key());
            claim.setInstanceId(context.instanceId());
            claim.initCreate(request.operatorId());
            claims.add(claim);
        }
        return new SubmissionResult(
                context.instanceId(),
                context.attemptId(),
                context.definitionVersion(),
                TaskInstanceStatus.SUBMITTED,
                false);
    }
}
