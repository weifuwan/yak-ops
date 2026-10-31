package io.yak.ops.business.task.submission.impl;

import io.yak.ops.business.task.submission.PreparedTaskSubmission;
import io.yak.ops.business.task.submission.ScheduleAdmissionController;
import io.yak.ops.business.task.submission.ScheduleFireResult;
import io.yak.ops.business.task.submission.ScheduleRequestId;
import io.yak.ops.business.task.submission.SubmissionResult;
import io.yak.ops.business.task.submission.TaskSubmissionTransactionService;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.schedule.ScheduleErrorCode;
import io.yak.ops.common.enums.schedule.ScheduleFireOutcome;
import io.yak.ops.common.enums.schedule.ScheduleFireReason;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.schedule.ScheduleEntity;
import io.yak.ops.dao.entity.schedule.ScheduleFireEntity;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.entity.task.TaskGroupQueueEntity;
import io.yak.ops.dao.repository.schedule.ScheduleFireRepository;
import io.yak.ops.dao.repository.schedule.ScheduleRepository;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import io.yak.ops.dao.repository.task.TaskGroupQueueRepository;
import io.yak.ops.plugin.scheduler.api.CronService;
import io.yak.ops.plugin.scheduler.api.ScheduleTrigger;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.workspace.WorkspaceService;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import org.apache.dolphinscheduler.registry.api.Registry;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * Atomically pins a published Definition, persists its Instance, and acquires all
 * declared resources. Physical execution must occur after this transaction commits.
 */
@Service
public class TaskSubmissionTransactionServiceImpl implements TaskSubmissionTransactionService {
    @Resource
    private DefinitionRepository definitions;

    @Resource
    private DefinitionVersionRepository versions;

    @Resource
    private InstanceRepository instances;

    @Resource
    private TaskGroupQueueRepository claims;

    @Resource
    private ScheduleRepository schedules;

    @Resource
    private ScheduleFireRepository fires;

    @Resource
    private Registry registry;

    @Resource
    private ScheduleAdmissionController admissionController;

    @Resource
    private WorkspaceService workspaces;

    @Resource
    private CronService cron;

    @Resource
    private SqlSessionTemplate sqlSession;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmissionResult admit(PreparedTaskSubmission request) {
        if (request.triggerType() == TaskTriggerType.SCHEDULE
                || request.scheduleId() != null
                || request.scheduleRevision() != null
                || request.scheduledTime() != null) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        TaskExecutionIdentity context = request.identity();
        String workspaceId = context.workspaceId();
        Optional<InstanceEntity> replay =
                instances.findByRequest(workspaceId, request.triggerType(), request.requestId());
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
                || !Objects.equals(definition.getPublishedVersion(), context.definitionVersion())
                || !Objects.equals(definition.getPublishedDebug(), request.debug())
                || !definition.getTaskType().equals(request.plugin().type())
                || versions.queryCurrent(workspaceId, definition.getId(), context.definitionVersion())
                        .isEmpty()) {
            throw new BusinessException(CommonErrorCode.RESOURCE_DUPLICATION);
        }

        // Resources were resolved before this transaction. Keep database row locks
        // short; the Worker revalidates the binding before starting physical execution.
        return persist(request, definition, request.plan());
    }

    private SubmissionResult persist(
            PreparedTaskSubmission request, DefinitionEntity definition, TaskExecutionResources currentPlan) {
        TaskExecutionIdentity context = request.identity();
        String workspaceId = context.workspaceId();
        InstanceEntity instance = new InstanceEntity();
        instance.setId(context.instanceId());
        instance.setWorkspaceId(workspaceId);
        instance.setDefinitionId(context.definitionId());
        instance.setDefinitionVersion(context.definitionVersion());
        instance.setDefinitionName(definition.getName());
        instance.setDebug(request.debug());
        instance.setTaskType(definition.getTaskType());
        instance.setTriggerType(request.triggerType());
        instance.setRequestId(request.requestId());
        instance.setScheduleId(request.scheduleId());
        instance.setScheduleRevision(request.scheduleRevision());
        if (request.scheduledTime() != null) {
            instance.setScheduledTime(utc(request.scheduledTime()));
        }
        instance.setRequestFingerprint(request.requestFingerprint());
        instance.setResourceRevision(currentPlan.resourceRevision());
        instance.setLogPath("tasks/" + context.instanceId() + "/task.log");
        instance.setStatus(TaskInstanceStatus.SUBMITTED);
        instance.setStateVersion(0);
        instance.initCreate(request.operatorId());
        instances.add(instance);

        for (ExecutionResource resource : currentPlan.exclusiveResources().stream()
                .sorted(Comparator.comparing(ExecutionResource::type).thenComparing(ExecutionResource::key))
                .toList()) {
            TaskGroupQueueEntity queue = new TaskGroupQueueEntity();
            queue.setWorkspaceId(workspaceId);
            queue.setResourceType(resource.type());
            queue.setResourceKey(resource.key());
            queue.setInstanceId(context.instanceId());
            queue.initCreate(request.operatorId());
            claims.add(queue);
        }
        // A multi-resource Task may not hold a subset of its slots while waiting.
        // Scheduled occurrences retain the existing RESOURCE_BUSY -> SKIPPED contract;
        // manual submissions remain durable and are awakened by TaskGroupCoordinator.
        if (!claims.tryAcquire(workspaceId, context.instanceId())
                && request.triggerType() == TaskTriggerType.SCHEDULE) {
            throw new ResourceBusyException(new DuplicateKeyException("TaskGroup slots are occupied"));
        }
        return new SubmissionResult(
                context.instanceId(), context.definitionVersion(), TaskInstanceStatus.SUBMITTED, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ScheduleFireResult admitScheduled(
            ScheduleTrigger trigger, PreparedTaskSubmission request, ScheduleFireReason preparationFailure) {
        admissionController.checkAdmission();
        ScheduleEntity selected = schedules
                .queryIncludingDeleted(trigger.workspaceId(), trigger.scheduleId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        DefinitionEntity definition = definitions
                .queryByIdIncludingDeletedForUpdate(trigger.workspaceId(), selected.getTargetId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        ScheduleEntity schedule = schedules
                .lockIncludingDeleted(trigger.workspaceId(), trigger.scheduleId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        String key = ScheduleRequestId.of(
                trigger.workspaceId(), trigger.scheduleId(), trigger.revision(), trigger.scheduledTime());
        String fingerprint = "SCHEDULE:" + schedule.getTargetId() + ":" + key;
        if (request != null) {
            validateScheduledRequest(request, schedule, trigger, key, fingerprint);
        }
        if (!registry.isConnected()
                || !admissionController.checkAdmission().equals(registry.get("/yak-ops/masters/embedded"))) {
            admissionController.stop();
            throw new IllegalStateException("Schedule database ownership was lost");
        }
        Optional<ScheduleFireEntity> fire = fires.findForUpdate(trigger.workspaceId(), key);
        Optional<InstanceEntity> instance =
                instances.findByRequestForUpdate(trigger.workspaceId(), TaskTriggerType.SCHEDULE, key);
        if (fire.isPresent() || instance.isPresent()) {
            if (!workspaces.isActiveMember(trigger.workspaceId(), schedule.getEnabledBy())) {
                throw new BusinessException(ScheduleErrorCode.ACCESS_DENIED);
            }
            if (instance.isPresent()) {
                InstanceEntity existing = instance.orElseThrow();
                if (!fingerprint.equals(existing.getRequestFingerprint())) {
                    throw new BusinessException(CommonErrorCode.RESOURCE_DUPLICATION);
                }
            }
            if (fire.isPresent()) {
                ScheduleFireEntity existing = fire.orElseThrow();
                boolean admitted = existing.getOutcome() == ScheduleFireOutcome.ADMITTED;
                if (admitted != instance.isPresent()
                        || (admitted
                                && !existing.getInstanceId()
                                        .equals(instance.orElseThrow().getId()))) {
                    throw new IllegalStateException("Schedule fire and Instance evidence are inconsistent");
                }
                return new ScheduleFireResult(
                        existing.getOutcome(), existing.getReason(), existing.getInstanceId(), true);
            }
            return new ScheduleFireResult(
                    ScheduleFireOutcome.ADMITTED, null, instance.orElseThrow().getId(), true);
        }
        ScheduleFireReason reason = guard(schedule, definition, trigger);
        if (reason != null) {
            return resolve(schedule, trigger, key, ScheduleFireOutcome.REJECTED, reason, null);
        }
        if (request != null && !Objects.equals(schedule.getEnabledBy(), request.operatorId())) {
            return resolve(schedule, trigger, key, ScheduleFireOutcome.REJECTED, ScheduleFireReason.UNAUTHORIZED, null);
        }
        if (preparationFailure != null) {
            ScheduleFireOutcome outcome = ScheduleFireOutcome.REJECTED;
            if (preparationFailure == ScheduleFireReason.PREPARATION_FAILED) {
                outcome = ScheduleFireOutcome.SUBMISSION_FAILED;
            }
            return resolve(schedule, trigger, key, outcome, preparationFailure, null);
        }
        if (request == null) {
            throw new IllegalStateException("New schedule occurrence has no prepared Task input");
        }
        if (!Objects.equals(definition.getPublishedVersion(), request.identity().definitionVersion())
                || !Objects.equals(definition.getPublishedDebug(), request.debug())
                || !definition.getTaskType().equals(request.plugin().type())
                || versions.queryCurrent(
                                trigger.workspaceId(),
                                definition.getId(),
                                request.identity().definitionVersion())
                        .isEmpty()) {
            return resolve(
                    schedule, trigger, key, ScheduleFireOutcome.REJECTED, ScheduleFireReason.DEFINITION_CHANGED, null);
        }
        if (!request.plugin().supportsScheduling(request.parameters())) {
            return resolve(
                    schedule, trigger, key, ScheduleFireOutcome.REJECTED, ScheduleFireReason.NOT_SCHEDULABLE, null);
        }
        // Schedules record a definitive BINDING_CHANGED outcome instead of an accepted
        // but subsequently rejected Instance. Keep this check until scheduled resource
        // resolution is moved into a separately prepared, transactional trigger protocol.
        TaskExecutionResources currentPlan = request.plugin().plan(request.parameters(), request.identity());
        if (!request.plan().equals(currentPlan)) {
            return resolve(
                    schedule, trigger, key, ScheduleFireOutcome.REJECTED, ScheduleFireReason.BINDING_CHANGED, null);
        }
        // The savepoint is inside this interceptor boundary: expected conflict never marks the outer transaction
        // rollback-only.
        TransactionStatus status = TransactionAspectSupport.currentTransactionStatus();
        Object savepoint = status.createSavepoint();
        try {
            SubmissionResult admitted = persist(request, definition, currentPlan);
            admissionController.checkAdmission();
            ScheduleFireReason late = timeGuard(schedule, trigger);
            if (late != null) {
                status.rollbackToSavepoint(savepoint);
                sqlSession.clearCache();
                return resolve(schedule, trigger, key, ScheduleFireOutcome.REJECTED, late, null);
            }
            return resolve(schedule, trigger, key, ScheduleFireOutcome.ADMITTED, null, admitted.instanceId());
        } catch (ResourceBusyException conflict) {
            status.rollbackToSavepoint(savepoint);
            sqlSession.clearCache();
            return resolve(schedule, trigger, key, ScheduleFireOutcome.SKIPPED, ScheduleFireReason.RESOURCE_BUSY, null);
        } finally {
            status.releaseSavepoint(savepoint);
        }
    }

    private void validateScheduledRequest(
            PreparedTaskSubmission request,
            ScheduleEntity schedule,
            ScheduleTrigger trigger,
            String key,
            String fingerprint) {
        if (request.triggerType() != TaskTriggerType.SCHEDULE
                || !trigger.workspaceId().equals(request.identity().workspaceId())
                || !schedule.getTargetId().equals(request.identity().definitionId())
                || !trigger.scheduleId().equals(request.scheduleId())
                || !Long.valueOf(trigger.revision()).equals(request.scheduleRevision())
                || !trigger.scheduledTime().equals(request.scheduledTime())
                || !key.equals(request.requestId())
                || !fingerprint.equals(request.requestFingerprint())) {
            throw new BusinessException(CommonErrorCode.RESOURCE_DUPLICATION);
        }
    }

    private ScheduleFireReason guard(ScheduleEntity schedule, DefinitionEntity definition, ScheduleTrigger trigger) {
        if (schedule.getDeletedAt() != null || !schedule.isEnabled() || schedule.getRevision() != trigger.revision()) {
            return ScheduleFireReason.STALE_SCHEDULE;
        }
        if (definition.getDeletedAt() != null) {
            return ScheduleFireReason.TARGET_DELETED;
        }
        if (definition.getStatus() != DefinitionStatus.PUBLISHED
                || definition.getPublishedVersion() == null
                || definition.getPublishedDebug() == null) {
            return ScheduleFireReason.TARGET_UNPUBLISHED;
        }
        if (!workspaces.isActiveMember(trigger.workspaceId(), schedule.getEnabledBy())) {
            return ScheduleFireReason.UNAUTHORIZED;
        }
        if (!cron.matches(schedule.getCronExpression(), schedule.getTimeZone(), trigger.scheduledTime())) {
            return ScheduleFireReason.INVALID_OCCURRENCE;
        }
        return timeGuard(schedule, trigger);
    }

    private ScheduleFireReason timeGuard(ScheduleEntity schedule, ScheduleTrigger trigger) {
        Instant now = admissionController.now();
        Instant time = trigger.scheduledTime();
        if (time.isAfter(now)
                || trigger.fireTime().isBefore(time)
                || trigger.fireTime().isAfter(now)) {
            return ScheduleFireReason.INVALID_OCCURRENCE;
        }
        if (schedule.getEnabledAt() == null
                || !time.isAfter(schedule.getEnabledAt().toInstant(ZoneOffset.UTC))
                || !time.isAfter(admissionController.activation())
                || !time.isAfter(trigger.activatedAt())
                || (schedule.getRetiredBefore() != null
                        && time.isBefore(schedule.getRetiredBefore().toInstant(ZoneOffset.UTC)))
                || Duration.between(time, now).compareTo(admissionController.maxLateness()) > 0) {
            return ScheduleFireReason.MISSED_WINDOW;
        }
        return null;
    }

    private ScheduleFireResult resolve(
            ScheduleEntity schedule,
            ScheduleTrigger trigger,
            String key,
            ScheduleFireOutcome outcome,
            ScheduleFireReason reason,
            String instanceId) {
        ScheduleFireEntity fire = new ScheduleFireEntity();
        fire.setWorkspaceId(trigger.workspaceId());
        fire.setScheduleId(trigger.scheduleId());
        fire.setScheduleRevision(trigger.revision());
        fire.setTargetType(schedule.getTargetType());
        fire.setTargetId(schedule.getTargetId());
        fire.setScheduledTime(utc(trigger.scheduledTime()));
        fire.setFireTime(utc(trigger.fireTime()));
        fire.setResolvedTime(utc(admissionController.now()));
        fire.setRequestId(key);
        fire.setOutcome(outcome);
        fire.setReason(reason);
        fire.setInstanceId(instanceId);
        String actor = schedule.getEnabledBy();
        if (actor == null) {
            actor = schedule.getCreateBy();
        }
        fire.initCreate(actor);
        fires.add(fire);
        return new ScheduleFireResult(outcome, reason, instanceId, false);
    }

    private static LocalDateTime utc(Instant time) {
        return LocalDateTime.ofInstant(time, ZoneOffset.UTC);
    }

    /** Identifies an actual occupied execution-resource row, not an arbitrary duplicate key. */
    private static final class ResourceBusyException extends RuntimeException {
        private ResourceBusyException(DuplicateKeyException cause) {
            super("Execution resource is occupied", cause);
        }
    }
}
