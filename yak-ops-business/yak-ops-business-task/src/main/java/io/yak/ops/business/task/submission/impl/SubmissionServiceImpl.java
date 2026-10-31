package io.yak.ops.business.task.submission.impl;

import io.yak.ops.business.task.submission.PreparedTaskSubmission;
import io.yak.ops.business.task.submission.ScheduleAdmissionController;
import io.yak.ops.business.task.submission.ScheduleFireResult;
import io.yak.ops.business.task.submission.ScheduleRequestId;
import io.yak.ops.business.task.submission.SubmissionResult;
import io.yak.ops.business.task.submission.SubmissionService;
import io.yak.ops.business.task.submission.TaskSubmissionCommand;
import io.yak.ops.business.task.submission.TaskSubmissionTransactionService;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.schedule.ScheduleErrorCode;
import io.yak.ops.common.enums.schedule.ScheduleFireOutcome;
import io.yak.ops.common.enums.schedule.ScheduleFireReason;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.util.IdUtils;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.dao.entity.schedule.ScheduleEntity;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.schedule.ScheduleFireRepository;
import io.yak.ops.dao.repository.schedule.ScheduleRepository;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import io.yak.ops.plugin.scheduler.api.ScheduleTrigger;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.TaskPluginManager;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.plugin.task.api.validation.TaskValidationContextFactory;
import io.yak.ops.workspace.WorkspaceService;
import jakarta.annotation.Resource;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Validates a request and prepares a read-only execution plan before durable admission.
 *
 * <p>Does not submit a physical Job; dispatch follows durable admission.
 */
@Service
public class SubmissionServiceImpl implements SubmissionService {
    private static final Logger LOG = LoggerFactory.getLogger(SubmissionServiceImpl.class);

    @Resource
    private ScheduleRepository schedules;

    @Resource
    private ScheduleFireRepository fires;

    @Resource
    private ScheduleAdmissionController admissionController;

    @Resource
    private WorkspaceService workspaces;

    @Resource
    private DefinitionRepository definitions;

    @Resource
    private DefinitionVersionRepository versions;

    @Resource
    private InstanceRepository instances;

    @Resource
    private TaskPluginManager plugins;

    @Resource
    private TaskValidationContextFactory validationContexts;

    @Resource
    private TaskSubmissionTransactionService admission;

    @Override
    public SubmissionResult submit(TaskSubmissionCommand command) {
        if (command == null
                || command.workspaceId() == null
                || command.workspaceId().isBlank()
                || command.triggerType() == null
                || command.triggerType() == TaskTriggerType.SCHEDULE
                || command.scheduleId() != null
                || command.scheduleRevision() != null
                || command.scheduledTime() != null
                || command.definitionId() == null
                || command.definitionId().isBlank()
                || command.requestId() == null
                || !command.requestId().matches("[A-Za-z0-9_-]{8,128}")) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        String workspaceId = command.workspaceId();
        String definitionId = command.definitionId();
        String requestId = command.requestId();
        String fingerprint = command.triggerType().name() + ":" + definitionId;
        Optional<InstanceEntity> existing = instances.findByRequest(workspaceId, command.triggerType(), requestId);
        if (existing.isPresent()) {
            return replay(existing.orElseThrow(), fingerprint);
        }

        PreparedTaskSubmission request = prepare(command, fingerprint);
        try {
            return admission.admit(request);
        } catch (DuplicateKeyException duplicate) {
            return instances
                    .findByRequest(workspaceId, command.triggerType(), requestId)
                    .map(instance -> replay(instance, fingerprint))
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_DUPLICATION));
        }
    }

    private PreparedTaskSubmission prepare(TaskSubmissionCommand command, String fingerprint) {
        String workspaceId = command.workspaceId();
        String definitionId = command.definitionId();
        String requestId = command.requestId();
        DefinitionEntity definition = definitions
                .queryById(workspaceId, definitionId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        if (definition.getStatus() != DefinitionStatus.PUBLISHED
                || definition.getPublishedVersion() == null
                || definition.getPublishedDebug() == null) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        TaskPlugin plugin = plugins.get(definition.getTaskType());
        if (!(plugin instanceof TaskChannel executable)) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        int version = definition.getPublishedVersion();
        boolean debug = definition.getPublishedDebug();
        DefinitionVersionEntity snapshot = versions.queryCurrent(workspaceId, definitionId, version)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        ReadonlyConfig parameters = executable.parse(snapshot.getParameterSchemaVersion(), snapshot.getParameters());
        if (command.triggerType() == TaskTriggerType.SCHEDULE && !executable.supportsScheduling(parameters)) {
            throw new BusinessException(ScheduleErrorCode.NOT_SCHEDULABLE);
        }
        executable.validate(parameters, validationContexts.forWorkspace(workspaceId));
        TaskExecutionIdentity context = new TaskExecutionIdentity(workspaceId, definitionId, version, IdUtils.nextId());
        TaskExecutionResources plan = executable.plan(parameters, context);
        return new PreparedTaskSubmission(
                context,
                debug,
                command.triggerType(),
                requestId,
                fingerprint,
                command.operatorId(),
                parameters,
                plan,
                executable,
                command.scheduleId(),
                command.scheduleRevision(),
                command.scheduledTime());
    }

    @Override
    public ScheduleFireResult submitScheduled(ScheduleTrigger trigger) {
        if (trigger == null
                || trigger.workspaceId() == null
                || trigger.workspaceId().isBlank()
                || trigger.scheduleId() == null
                || trigger.scheduleId().isBlank()
                || trigger.revision() < 1
                || trigger.scheduledTime() == null
                || trigger.fireTime() == null
                || trigger.activatedAt() == null
                || trigger.scheduledTime().getNano() % 1_000_000 != 0) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        try (ScheduleAdmissionController.Permit ignored = admissionController.enter()) {
            Optional<ScheduleEntity> selected =
                    schedules.queryIncludingDeleted(trigger.workspaceId(), trigger.scheduleId());
            if (selected.isEmpty()) {
                return new ScheduleFireResult(
                        ScheduleFireOutcome.REJECTED, ScheduleFireReason.STALE_SCHEDULE, null, false);
            }
            ScheduleEntity schedule = selected.orElseThrow();
            String key = ScheduleRequestId.of(
                    trigger.workspaceId(), trigger.scheduleId(), trigger.revision(), trigger.scheduledTime());
            if (fires.find(trigger.workspaceId(), key).isPresent()
                    || instances
                            .findByRequest(trigger.workspaceId(), TaskTriggerType.SCHEDULE, key)
                            .isPresent()
                    || schedule.getDeletedAt() != null
                    || !schedule.isEnabled()
                    || schedule.getRevision() != trigger.revision()) {
                return admission.admitScheduled(trigger, null, null);
            }
            if (!workspaces.isActiveMember(trigger.workspaceId(), schedule.getEnabledBy())) {
                return admission.admitScheduled(trigger, null, ScheduleFireReason.UNAUTHORIZED);
            }
            TaskSubmissionCommand command = new TaskSubmissionCommand(
                    trigger.workspaceId(),
                    schedule.getTargetId(),
                    TaskTriggerType.SCHEDULE,
                    key,
                    schedule.getEnabledBy(),
                    trigger.scheduleId(),
                    trigger.revision(),
                    trigger.scheduledTime());
            PreparedTaskSubmission prepared = null;
            ScheduleFireReason failure = null;
            try {
                prepared = prepare(command, "SCHEDULE:" + schedule.getTargetId() + ":" + key);
                if (!prepared.plugin().supportsScheduling(prepared.parameters())) {
                    failure = ScheduleFireReason.NOT_SCHEDULABLE;
                    prepared = null;
                }
            } catch (DataAccessException infrastructureFailure) {
                // Storage uncertainty cannot be persisted as a successful skip.
                throw infrastructureFailure;
            } catch (BusinessException | TaskPluginException invalid) {
                failure = ScheduleFireReason.PREPARATION_FAILED;
                if (invalid instanceof BusinessException business
                        && business.getErrorCode() == ScheduleErrorCode.NOT_SCHEDULABLE) {
                    failure = ScheduleFireReason.NOT_SCHEDULABLE;
                }
                LOG.warn(
                        "Scheduled submission preparation was rejected, scheduleId={}, exceptionType={}",
                        trigger.scheduleId(),
                        invalid.getClass().getSimpleName());
            }
            ScheduleFireResult result = admission.admitScheduled(trigger, prepared, failure);
            if (!result.replayed()) {
                LOG.info(
                        "Schedule fire resolved, scheduleId={}, revision={}, scheduledTime={}, outcome={}, reason={}, triggerLagMs={}",
                        trigger.scheduleId(),
                        trigger.revision(),
                        trigger.scheduledTime(),
                        result.outcome(),
                        result.reason(),
                        java.time.Duration.between(trigger.scheduledTime(), trigger.fireTime())
                                .toMillis());
            }
            return result;
        }
    }

    private SubmissionResult replay(InstanceEntity instance, String fingerprint) {
        if (!fingerprint.equals(instance.getRequestFingerprint())) {
            throw new BusinessException(CommonErrorCode.RESOURCE_DUPLICATION);
        }
        return new SubmissionResult(instance.getId(), instance.getDefinitionVersion(), instance.getStatus(), true);
    }
}
