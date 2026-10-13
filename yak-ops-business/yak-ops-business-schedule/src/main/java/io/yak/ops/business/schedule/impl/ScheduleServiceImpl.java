package io.yak.ops.business.schedule.impl;

import io.yak.ops.business.schedule.ScheduleService;
import io.yak.ops.common.bean.dto.schedule.CreateScheduleDTO;
import io.yak.ops.common.bean.dto.schedule.SchedulePreviewDTO;
import io.yak.ops.common.bean.dto.schedule.ScheduleQueryDTO;
import io.yak.ops.common.bean.dto.schedule.UpdateScheduleDTO;
import io.yak.ops.common.bean.vo.schedule.SchedulePreviewVO;
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
import io.yak.ops.dao.repository.schedule.SchedulePageQuery;
import io.yak.ops.dao.repository.schedule.ScheduleRepository;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.plugin.scheduler.api.CronService;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private TaskPluginRegistry plugins;

    @Resource
    private CronService cron;

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
        return schedules
                .queryPage(new SchedulePageQuery(
                        workspaceId,
                        dto.getPageNo(),
                        dto.getPageSize(),
                        dto.getTargetType(),
                        dto.getTargetId(),
                        dto.getEnabled()))
                .map(this::summary);
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
    }

    @Override
    public SchedulePreviewVO preview(SchedulePreviewDTO dto) {
        if (dto == null) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        Instant after = dto.getAfter();
        if (after == null) {
            after = Instant.now();
        }
        after = after.truncatedTo(ChronoUnit.MILLIS);
        try {
            return new SchedulePreviewVO(
                    after, cron.nextFireTimes(dto.getCronExpression(), dto.getTimeZone(), after, 5));
        } catch (IllegalArgumentException failure) {
            throw new BusinessException(ScheduleErrorCode.INVALID_TIME_RULE);
        }
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

    private ScheduleSummaryVO summary(ScheduleEntity entity) {
        ScheduleSummaryVO result = BeanCopyUtils.copy(entity, ScheduleSummaryVO.class);
        result.setRegistrationStatus("PENDING");
        return result;
    }

    private ScheduleVO detail(ScheduleEntity entity, DefinitionEntity definition) {
        ScheduleVO result = BeanCopyUtils.copy(entity, ScheduleVO.class);
        result.setRegistrationStatus("PENDING");
        if (definition.getDeletedAt() != null) {
            result.setEligibilityReason("TARGET_DELETED");
            return result;
        }
        if (definition.getStatus() != DefinitionStatus.PUBLISHED) {
            result.setEligibilityReason("TARGET_UNPUBLISHED");
            return result;
        }
        var plugin = plugins.get(definition.getTaskType());
        if (!(plugin instanceof ExecutableTaskPlugin executable)) {
            result.setEligibilityReason("NOT_SCHEDULABLE");
            return result;
        }
        var version = versions.queryCurrent(entity.getWorkspaceId(), definition.getId(), definition.getCurrentVersion())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        boolean eligible = executable.supportsScheduling(executable.parse(version.getParameters()));
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
