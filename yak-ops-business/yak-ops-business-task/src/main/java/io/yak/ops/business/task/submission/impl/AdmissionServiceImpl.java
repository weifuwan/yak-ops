package io.yak.ops.business.task.submission.impl;

import io.yak.ops.business.task.submission.AdmissionRequest;
import io.yak.ops.business.task.submission.AdmissionService;
import io.yak.ops.business.task.submission.SubmissionResult;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.ExecutionClaimEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.dao.repository.task.ExecutionClaimRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskAdmissionPlan;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomically pins a published Definition, persists its Instance, and acquires all
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
    private ExecutionClaimRepository claims;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmissionResult admit(AdmissionRequest request) {
        TaskExecutionIdentity context = request.identity();
        String workspaceId = context.workspaceId();
        var replay = instances.findByRequest(workspaceId, request.triggerType(), request.requestId());
        if (replay.isPresent()) {
            InstanceEntity existing = replay.orElseThrow();
            if (!existing.getRequestFingerprint().equals(request.requestFingerprint())) {
                throw new BusinessException(CommonErrorCode.RESOURCE_DUPLICATION);
            }
            return new SubmissionResult(existing.getId(), existing.getDefinitionVersion(), existing.getStatus(), true);
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

        TaskAdmissionPlan currentPlan = request.plugin().plan(request.parameters(), context);
        if (!request.plan().equals(currentPlan)) {
            throw new BusinessException(CommonErrorCode.RESOURCE_DUPLICATION);
        }

        InstanceEntity instance = new InstanceEntity();
        instance.setId(context.instanceId());
        instance.setWorkspaceId(workspaceId);
        instance.setDefinitionId(context.definitionId());
        instance.setDefinitionVersion(context.definitionVersion());
        instance.setDebug(definition.isDebug());
        instance.setTaskType(definition.getTaskType());
        instance.setTriggerType(request.triggerType());
        instance.setRequestId(request.requestId());
        instance.setRequestFingerprint(request.requestFingerprint());
        instance.setResourceRevision(currentPlan.resourceRevision());
        instance.setLogPath("tasks/" + context.instanceId() + "/task.log");
        instance.setStatus(TaskInstanceStatus.SUBMITTED);
        instance.setStateVersion(0);
        instance.initCreate(request.operatorId());
        instances.add(instance);

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
                context.instanceId(), context.definitionVersion(), TaskInstanceStatus.SUBMITTED, false);
    }
}
