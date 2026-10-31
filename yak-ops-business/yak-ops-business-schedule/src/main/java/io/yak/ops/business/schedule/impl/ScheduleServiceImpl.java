package io.yak.ops.business.schedule.impl;

import io.yak.ops.business.schedule.ScheduleRegistrationService;
import io.yak.ops.business.schedule.ScheduleService;
import io.yak.ops.common.bean.dto.schedule.CreateScheduleDTO;
import io.yak.ops.common.bean.dto.schedule.ScheduleFireQueryDTO;
import io.yak.ops.common.bean.dto.schedule.SchedulePreviewDTO;
import io.yak.ops.common.bean.dto.schedule.ScheduleQueryDTO;
import io.yak.ops.common.bean.dto.schedule.UpdateScheduleDTO;
import io.yak.ops.common.bean.vo.schedule.ScheduleFireVO;
import io.yak.ops.common.bean.vo.schedule.SchedulePreviewVO;
import io.yak.ops.common.bean.vo.schedule.ScheduleRegistrationVO;
import io.yak.ops.common.bean.vo.schedule.ScheduleSummaryVO;
import io.yak.ops.common.bean.vo.schedule.ScheduleVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.schedule.ScheduleErrorCode;
import io.yak.ops.common.enums.schedule.ScheduleTargetType;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.dao.entity.schedule.ScheduleEntity;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.repository.schedule.ScheduleFireRepository;
import io.yak.ops.dao.repository.schedule.SchedulePageQuery;
import io.yak.ops.dao.repository.schedule.ScheduleRepository;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.plugin.scheduler.api.CronService;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginManager;
import io.yak.ops.workspace.WorkspaceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Saves disabled schedule intent and obtains metadata through the existing Task/plugin boundary. */
@Service
public class ScheduleServiceImpl implements ScheduleService {
    @Resource
    private ScheduleRepository schedules;

    @Resource
    private DefinitionRepository definitions;

    @Resource
    private DefinitionVersionRepository versions;

    @Resource
    private TaskPluginManager plugins;

    @Resource
    private CronService cron;

    @Resource
    private ScheduleRegistrationService registrations;

    @Resource
    private ScheduleFireRepository fireRepository;

    @Resource
    private WorkspaceService workspaces;

    @Resource
    private Clock scheduleClock;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO create(String workspaceId, CreateScheduleDTO dto, String operatorId) {
        requireActor(workspaceId, operatorId);
        if (dto == null
                || dto.getTargetType() != ScheduleTargetType.TASK
                || dto.getTargetId() == null
                || dto.getTargetId().isBlank()
                || dto.getTargetId().length() > 64) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        validateTimeRule(dto.getCronExpression(), dto.getTimeZone());
        DefinitionEntity definition = definitions
                .queryByIdForUpdate(workspaceId, dto.getTargetId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        if (schedules
                .queryByTarget(workspaceId, dto.getTargetType(), dto.getTargetId())
                .isPresent()) {
            throw new BusinessException(ScheduleErrorCode.TARGET_ALREADY_SCHEDULED);
        }
        ScheduleEntity entity = new ScheduleEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setTargetType(dto.getTargetType());
        entity.setTargetId(dto.getTargetId());
        entity.setCronExpression(dto.getCronExpression());
        entity.setTimeZone(dto.getTimeZone());
        entity.setEnabled(false);
        entity.setRevision(1L);
        entity.initCreate(operatorId);
        schedules.add(entity);
        synchronizeAfterCommit(workspaceId, entity.getId());
        return detail(entity, definition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO update(String workspaceId, String id, UpdateScheduleDTO dto, String operatorId) {
        requireActor(workspaceId, operatorId);
        if (dto == null || dto.getRevision() == null || dto.getRevision() < 1) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        validateTimeRule(dto.getCronExpression(), dto.getTimeZone());
        ScheduleEntity selected = requireSchedule(workspaceId, id);
        DefinitionEntity definition = definitions
                .queryByIdForUpdate(workspaceId, selected.getTargetId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        ScheduleEntity entity = lockSchedule(workspaceId, id);
        requireRevision(entity, dto.getRevision());
        if (entity.isEnabled()) {
            throw new BusinessException(ScheduleErrorCode.MUST_BE_DISABLED);
        }
        if (entity.getCronExpression().equals(dto.getCronExpression())
                && entity.getTimeZone().equals(dto.getTimeZone())) {
            return detail(entity, definition);
        }
        entity.setCronExpression(dto.getCronExpression());
        entity.setTimeZone(dto.getTimeZone());
        entity.setRevision(Math.addExact(entity.getRevision(), 1));
        entity.initUpdate(operatorId);
        if (!schedules.updateDisabled(entity, dto.getRevision())) {
            throw new BusinessException(ScheduleErrorCode.REVISION_CONFLICT);
        }
        synchronizeAfterCommit(workspaceId, id);
        return detail(entity, definition);
    }

    @Override
    public ScheduleVO query(String workspaceId, String id) {
        ScheduleEntity entity = requireSchedule(workspaceId, id);
        DefinitionEntity definition = definitions
                .queryByIdIncludingDeleted(workspaceId, entity.getTargetId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        return detail(entity, definition);
    }

    @Override
    public PageData<ScheduleSummaryVO> queryPage(String workspaceId, ScheduleQueryDTO dto) {
        requireWorkspace(workspaceId);
        if (dto == null
                || dto.getPageNo() == null
                || dto.getPageSize() == null
                || dto.getPageNo() < 1
                || dto.getPageSize() < 1
                || dto.getPageSize() > 200
                || dto.getSorts() == null
                || !dto.getSorts().isEmpty()) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        if (dto.getTargetIds() != null
                && (dto.getTargetId() != null
                        || dto.getTargetIds().isEmpty()
                        || dto.getTargetIds().size() > 200
                        || dto.getTargetIds().stream()
                                .anyMatch(id -> id == null || id.isBlank() || id.length() > 64))) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        PageData<ScheduleEntity> page = schedules.queryPage(new SchedulePageQuery(
                workspaceId,
                dto.getPageNo(),
                dto.getPageSize(),
                dto.getTargetType(),
                dto.getTargetId(),
                dto.getTargetIds(),
                dto.getEnabled()));
        Map<String, ScheduleRegistrationVO> observations = registrations.describe(page.records());
        return page.map(row -> summary(row, observations.get(row.getId())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String workspaceId, String id, long revision, String operatorId) {
        requireActor(workspaceId, operatorId);
        if (revision < 1) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        ScheduleEntity selected = requireSchedule(workspaceId, id);
        definitions
                .queryByIdIncludingDeletedForUpdate(workspaceId, selected.getTargetId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        ScheduleEntity entity = lockSchedule(workspaceId, id);
        requireRevision(entity, revision);
        if (!schedules.softDelete(workspaceId, id, revision, DateUtils.now(), operatorId)) {
            throw new BusinessException(ScheduleErrorCode.REVISION_CONFLICT);
        }
        synchronizeAfterCommit(workspaceId, id);
    }

    @Override
    public SchedulePreviewVO preview(SchedulePreviewDTO dto) {
        if (dto == null) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        Instant after = dto.getAfter();
        if (after == null) {
            after = scheduleClock.instant();
        }
        after = after.truncatedTo(ChronoUnit.MILLIS);
        try {
            return new SchedulePreviewVO(
                    after, cron.nextFireTimes(dto.getCronExpression(), dto.getTimeZone(), after, 5));
        } catch (IllegalArgumentException failure) {
            throw new BusinessException(ScheduleErrorCode.INVALID_TIME_RULE);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO enable(String workspaceId, String id, long revision, String operatorId) {
        return changeEnablement(workspaceId, id, revision, operatorId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO disable(String workspaceId, String id, long revision, String operatorId) {
        return changeEnablement(workspaceId, id, revision, operatorId, false);
    }

    private ScheduleVO changeEnablement(String workspaceId, String id, long revision, String actor, boolean enabled) {
        requireActor(workspaceId, actor);
        if (revision < 1) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        ScheduleEntity selected = requireSchedule(workspaceId, id);
        DefinitionEntity definition = definitions
                .queryByIdIncludingDeletedForUpdate(workspaceId, selected.getTargetId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        ScheduleEntity schedule = lockSchedule(workspaceId, id);
        if (!workspaces.isActiveMember(workspaceId, actor)) {
            throw new BusinessException(ScheduleErrorCode.ACCESS_DENIED);
        }
        if (schedule.isEnabled() == enabled) {
            return detail(schedule, definition);
        }
        requireRevision(schedule, revision);
        Instant now = scheduleClock.instant().truncatedTo(ChronoUnit.MILLIS);
        if (enabled) {
            if (definition.getDeletedAt() != null
                    || definition.getStatus() != DefinitionStatus.PUBLISHED
                    || definition.getPublishedVersion() == null
                    || definition.getPublishedDebug() == null) {
                throw new BusinessException(ScheduleErrorCode.TARGET_UNPUBLISHED);
            }
            if (!detail(schedule, definition).isSchedulable()) {
                throw new BusinessException(ScheduleErrorCode.NOT_SCHEDULABLE);
            }
            if (cron.nextFireTimes(schedule.getCronExpression(), schedule.getTimeZone(), now, 1)
                    .isEmpty()) {
                throw new BusinessException(ScheduleErrorCode.NO_FUTURE_FIRE);
            }
        }
        LocalDateTime at = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        if (!schedules.setEnabled(workspaceId, id, revision, enabled, at, actor)) {
            throw new BusinessException(ScheduleErrorCode.REVISION_CONFLICT);
        }
        schedule.setEnabled(enabled);
        schedule.setRevision(Math.addExact(revision, 1));
        if (enabled) {
            schedule.setEnabledAt(at);
            schedule.setEnabledBy(actor);
        }
        synchronizeAfterCommit(workspaceId, id);
        return detail(schedule, definition);
    }

    @Override
    public PageData<ScheduleFireVO> fires(String workspaceId, String id, ScheduleFireQueryDTO query) {
        requireWorkspace(workspaceId);
        if (query == null
                || query.getPageNo() == null
                || query.getPageNo() < 1
                || query.getPageSize() == null
                || query.getPageSize() < 1
                || query.getPageSize() > 200
                || query.getSorts() == null
                || !query.getSorts().isEmpty()
                || (query.getFrom() != null
                        && query.getUntil() != null
                        && !query.getFrom().isBefore(query.getUntil()))) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        ScheduleEntity schedule = schedules
                .queryIncludingDeleted(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        definitions
                .queryByIdIncludingDeleted(workspaceId, schedule.getTargetId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        LocalDateTime from = null;
        LocalDateTime until = null;
        if (query.getFrom() != null) {
            from = LocalDateTime.ofInstant(query.getFrom(), ZoneOffset.UTC);
        }
        if (query.getUntil() != null) {
            until = LocalDateTime.ofInstant(query.getUntil(), ZoneOffset.UTC);
        }
        return fireRepository
                .page(workspaceId, id, query.getPageNo(), query.getPageSize(), query.getOutcome(), from, until)
                .map(fire -> new ScheduleFireVO(
                        fire.getId(),
                        fire.getScheduleId(),
                        fire.getScheduleRevision(),
                        fire.getScheduledTime().toInstant(ZoneOffset.UTC),
                        fire.getFireTime().toInstant(ZoneOffset.UTC),
                        fire.getResolvedTime().toInstant(ZoneOffset.UTC),
                        fire.getOutcome(),
                        fire.getReason(),
                        fire.getInstanceId()));
    }

    private void synchronizeAfterCommit(String workspaceId, String id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                registrations.synchronize(workspaceId, id);
            }
        });
    }

    private static void describe(ScheduleSummaryVO result, ScheduleRegistrationVO observation) {
        result.setRegistrationStatus(observation.status());
        result.setRegistrationReason(observation.reason());
        result.setRegistrationObservedTime(observation.observedTime());
        result.setNextFireTime(observation.nextFireTime());
        result.setExhausted(observation.exhausted());
    }

    private ScheduleEntity requireSchedule(String workspaceId, String id) {
        requireWorkspace(workspaceId);
        return schedules
                .queryById(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private ScheduleEntity lockSchedule(String workspaceId, String id) {
        return schedules
                .queryByIdForUpdate(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private void requireRevision(ScheduleEntity entity, long revision) {
        if (entity.getRevision() != revision) {
            throw new BusinessException(ScheduleErrorCode.REVISION_CONFLICT);
        }
    }

    private void validateTimeRule(String expression, String zone) {
        try {
            cron.validate(expression, zone);
        } catch (IllegalArgumentException failure) {
            throw new BusinessException(ScheduleErrorCode.INVALID_TIME_RULE);
        }
    }

    private ScheduleSummaryVO summary(ScheduleEntity entity, ScheduleRegistrationVO observation) {
        ScheduleSummaryVO result = BeanCopyUtils.copy(entity, ScheduleSummaryVO.class);
        describe(result, observation);
        return result;
    }

    private ScheduleVO detail(ScheduleEntity entity, DefinitionEntity definition) {
        ScheduleVO result = BeanCopyUtils.copy(entity, ScheduleVO.class);
        describe(result, registrations.describe(List.of(entity)).get(entity.getId()));
        if (definition.getDeletedAt() != null) {
            result.setEligibilityReason("TARGET_DELETED");
            return result;
        }
        if (definition.getStatus() != DefinitionStatus.PUBLISHED
                || definition.getPublishedVersion() == null
                || definition.getPublishedDebug() == null) {
            result.setEligibilityReason("TARGET_UNPUBLISHED");
            return result;
        }
        TaskPlugin plugin = plugins.get(definition.getTaskType());
        if (!(plugin instanceof TaskChannel executable)) {
            result.setEligibilityReason("NOT_SCHEDULABLE");
            return result;
        }
        DefinitionVersionEntity version = versions.queryCurrent(
                        entity.getWorkspaceId(), definition.getId(), definition.getPublishedVersion())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        boolean eligible = executable.supportsScheduling(
                executable.parse(version.getParameterSchemaVersion(), version.getParameters()));
        result.setSchedulable(eligible);
        if (!eligible) {
            result.setEligibilityReason("NOT_SCHEDULABLE");
        }
        return result;
    }

    private void requireWorkspace(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
    }

    private void requireActor(String workspaceId, String operatorId) {
        requireWorkspace(workspaceId);
        if (operatorId == null || operatorId.isBlank()) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
    }
}
