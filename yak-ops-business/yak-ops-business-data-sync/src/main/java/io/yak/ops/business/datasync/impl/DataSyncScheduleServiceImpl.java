package io.yak.ops.business.datasync.impl;

import io.yak.ops.business.datasync.DataSyncScheduleService;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.business.datasync.scheduler.DataSyncScheduleDefinition;
import io.yak.ops.business.datasync.scheduler.DataSyncScheduleFire;
import io.yak.ops.business.datasync.scheduler.DataSyncScheduleFireListener;
import io.yak.ops.business.datasync.scheduler.ScheduleEngine;
import io.yak.ops.business.datasync.scheduler.ScheduleEngineException;
import io.yak.ops.common.bean.dto.datasync.DataSyncScheduleDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSchedulePreviewVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncScheduleVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.DataSyncScheduleEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncScheduleRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTaskRepository;
import jakarta.annotation.Resource;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * DataSyncScheduleService 的业务职责实现。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Service
public class DataSyncScheduleServiceImpl implements DataSyncScheduleService, DataSyncScheduleFireListener {

    @Resource
    private DataSyncTaskRepository taskRepository;

    @Resource
    private DataSyncScheduleRepository scheduleRepository;

    @Resource
    private DataSyncInstanceRepository instanceRepository;

    @Resource
    private ScheduleEngine scheduleEngine;

    @Resource
    private DataSyncTaskDefinitionValidator definitionValidator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncScheduleVO saveSchedule(String taskId, DataSyncScheduleDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE);
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity task = requireTask(workspaceId, taskId);
        requireOfflineTask(task);

        String cronExpression = StringUtils.trimToNull(dto.getCronExpression());
        String timeZone = normalizeTimeZone(dto.getTimeZone());
        if (cronExpression == null) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "Cron 表达式不能为空");
        }

        DataSyncScheduleEntity schedule =
                scheduleRepository.queryByTask(workspaceId, taskId).orElse(null);
        if (schedule == null) {
            schedule = new DataSyncScheduleEntity();
            schedule.setWorkspaceId(workspaceId);
            schedule.setTaskId(taskId);
            schedule.setEnabled(false);
            schedule.setCronExpression(cronExpression);
            schedule.setTimeZone(timeZone);
            schedule.initCreate();
            validateScheduleDefinition(toScheduleDefinition(schedule));
            if (scheduleRepository.add(schedule) == null) {
                throw new DataSyncException(DataSyncErrorCode.SCHEDULE_PERSIST_FAILED);
            }
        } else {
            schedule.setCronExpression(cronExpression);
            schedule.setTimeZone(timeZone);
            schedule.initUpdate();
            validateScheduleDefinition(toScheduleDefinition(schedule));
            if (scheduleRepository.update(workspaceId, schedule) == null) {
                throw new DataSyncException(DataSyncErrorCode.SCHEDULE_PERSIST_FAILED);
            }
            if (Boolean.TRUE.equals(schedule.getEnabled())) {
                requireTaskStatus(task, DataSyncTaskStatus.PUBLISHED, "启用中的调度要求任务保持上线");
                replaceScheduleAfterCommit(schedule);
            }
        }
        return toScheduleVO(schedule);
    }

    @Override
    public DataSyncSchedulePreviewVO previewSchedule(DataSyncScheduleDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE);

        String cronExpression = StringUtils.trimToNull(dto.getCronExpression());
        String timeZone = normalizeTimeZone(dto.getTimeZone());
        if (cronExpression == null) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "Cron 表达式不能为空");
        }

        ZoneId zoneId = ZoneId.of(timeZone);
        List<LocalDateTime> nextFireTimes;
        try {
            nextFireTimes = scheduleEngine.previewNextFireTimes(cronExpression, zoneId, 5).stream()
                    .map(instant -> LocalDateTime.ofInstant(instant, zoneId))
                    .toList();
        } catch (ScheduleEngineException | IllegalArgumentException exception) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "Cron 表达式不合法", exception);
        }

        DataSyncSchedulePreviewVO result = new DataSyncSchedulePreviewVO();
        result.setCronExpression(cronExpression);
        result.setTimeZone(timeZone);
        result.setNextFireTimes(nextFireTimes);
        return result;
    }

    @Override
    public DataSyncScheduleVO querySchedule(String taskId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        requireOfflineTask(requireTask(workspaceId, taskId));
        return scheduleRepository
                .queryByTask(workspaceId, taskId)
                .map(this::toScheduleVO)
                .orElse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncScheduleVO enableSchedule(String taskId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        DataSyncTaskEntity task = requireTask(workspaceId, taskId);
        requireOfflineTask(task);
        requireTaskStatus(task, DataSyncTaskStatus.PUBLISHED, "任务上线后才能启用调度");

        DataSyncScheduleEntity schedule = requireSchedule(workspaceId, taskId);
        validateScheduleDefinition(toScheduleDefinition(schedule));
        if (!Boolean.TRUE.equals(schedule.getEnabled())) {
            schedule.setEnabled(true);
            schedule.initUpdate();
            if (scheduleRepository.update(workspaceId, schedule) == null) {
                throw new DataSyncException(DataSyncErrorCode.SCHEDULE_PERSIST_FAILED);
            }
        }
        replaceScheduleAfterCommit(schedule);
        return toScheduleVO(schedule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSyncScheduleVO disableSchedule(String taskId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        requireOfflineTask(requireTask(workspaceId, taskId));
        DataSyncScheduleEntity schedule = requireSchedule(workspaceId, taskId);
        if (Boolean.TRUE.equals(schedule.getEnabled())) {
            schedule.setEnabled(false);
            schedule.initUpdate();
            if (scheduleRepository.update(workspaceId, schedule) == null) {
                throw new DataSyncException(DataSyncErrorCode.SCHEDULE_PERSIST_FAILED);
            }
        }
        unscheduleAfterCommit(schedule.getId());
        return toScheduleVO(schedule);
    }

    @Override
    public void restoreScheduleRuntime() {
        for (DataSyncScheduleEntity schedule : scheduleRepository.queryEnabled()) {
            DataSyncTaskEntity task = taskRepository
                    .queryById(schedule.getWorkspaceId(), schedule.getTaskId())
                    .orElseThrow(() -> new DataSyncException(
                            DataSyncErrorCode.SCHEDULE_RUNTIME_FAILED, "启用中的调度关联任务不存在，scheduleId=" + schedule.getId()));
            requireOfflineTask(task);
            requireTaskStatus(task, DataSyncTaskStatus.PUBLISHED, "启用中的调度关联任务必须保持上线");
            validateScheduleDefinition(toScheduleDefinition(schedule));
            replaceScheduleRuntime(schedule);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized void onFire(DataSyncScheduleFire fire) {
        if (fire == null) throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE);
        WorkspaceContext.bind(fire.workspaceId());
        try {
            DataSyncScheduleEntity schedule = scheduleRepository
                    .queryById(fire.workspaceId(), fire.scheduleId())
                    .orElse(null);
            if (schedule == null
                    || !Boolean.TRUE.equals(schedule.getEnabled())
                    || !Objects.equals(schedule.getTaskId(), fire.taskId())) {
                return;
            }
            DataSyncTaskEntity task =
                    taskRepository.queryById(fire.workspaceId(), fire.taskId()).orElse(null);
            if (task == null
                    || task.getSyncType() != DataSyncType.OFFLINE
                    || task.getStatus() != DataSyncTaskStatus.PUBLISHED
                    || instanceRepository.existsActiveByTask(fire.workspaceId(), fire.taskId())) {
                return;
            }
            definitionValidator.validatePersistedTaskDefinition(task);
            throw runtimeUnavailable();
        } finally {
            WorkspaceContext.clear();
        }
    }

    private void requireOfflineTask(DataSyncTaskEntity task) {
        if (task == null || task.getSyncType() != DataSyncType.OFFLINE) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "只有离线同步任务支持 Cron 调度");
        }
    }

    private DataSyncScheduleEntity requireSchedule(String workspaceId, String taskId) {
        return scheduleRepository
                .queryByTask(workspaceId, taskId)
                .orElseThrow(() -> new DataSyncException(DataSyncErrorCode.SCHEDULE_NOT_FOUND));
    }

    private String normalizeTimeZone(String value) {
        String timeZone = StringUtils.trimToNull(value);
        if (timeZone == null) throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "时区不能为空");
        try {
            return ZoneId.of(timeZone).getId();
        } catch (DateTimeException exception) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "时区不合法", exception);
        }
    }

    private DataSyncScheduleDefinition toScheduleDefinition(DataSyncScheduleEntity schedule) {
        try {
            return new DataSyncScheduleDefinition(
                    schedule.getId(),
                    schedule.getWorkspaceId(),
                    schedule.getTaskId(),
                    schedule.getCronExpression(),
                    ZoneId.of(schedule.getTimeZone()));
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "调度定义不完整", exception);
        }
    }

    private void validateScheduleDefinition(DataSyncScheduleDefinition definition) {
        try {
            scheduleEngine.validate(definition);
        } catch (ScheduleEngineException exception) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_SCHEDULE, "Cron 表达式不合法", exception);
        }
    }

    private void replaceScheduleAfterCommit(DataSyncScheduleEntity schedule) {
        runAfterCommit(() -> replaceScheduleRuntime(schedule));
    }

    private void unscheduleAfterCommit(String scheduleId) {
        runAfterCommit(() -> {
            try {
                scheduleEngine.unschedule(scheduleId);
            } catch (ScheduleEngineException exception) {
                throw new DataSyncException(DataSyncErrorCode.SCHEDULE_RUNTIME_FAILED, "移除调度失败", exception);
            }
        });
    }

    private void replaceScheduleRuntime(DataSyncScheduleEntity schedule) {
        try {
            scheduleEngine.unschedule(schedule.getId());
            scheduleEngine.schedule(toScheduleDefinition(schedule));
        } catch (ScheduleEngineException exception) {
            throw new DataSyncException(DataSyncErrorCode.SCHEDULE_RUNTIME_FAILED, "注册调度失败", exception);
        }
    }

    public void disableForTask(String workspaceId, String taskId) {
        DataSyncScheduleEntity schedule =
                scheduleRepository.queryByTask(workspaceId, taskId).orElse(null);
        if (schedule == null) return;
        if (Boolean.TRUE.equals(schedule.getEnabled())) {
            schedule.setEnabled(false);
            schedule.initUpdate();
            if (scheduleRepository.update(workspaceId, schedule) == null) {
                throw new DataSyncException(DataSyncErrorCode.SCHEDULE_PERSIST_FAILED);
            }
        }
        unscheduleAfterCommit(schedule.getId());
    }

    public void deleteForTask(String workspaceId, String taskId) {
        DataSyncScheduleEntity schedule =
                scheduleRepository.queryByTask(workspaceId, taskId).orElse(null);
        if (schedule == null) return;
        if (scheduleRepository.deleteByTask(workspaceId, taskId) <= 0) {
            throw new DataSyncException(DataSyncErrorCode.SCHEDULE_PERSIST_FAILED, "删除任务调度失败");
        }
        unscheduleAfterCommit(schedule.getId());
    }

    private DataSyncScheduleVO toScheduleVO(DataSyncScheduleEntity entity) {
        return BeanCopyUtils.copy(entity, DataSyncScheduleVO.class);
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private DataSyncTaskStatus taskStatus(DataSyncTaskEntity task) {
        return task.getStatus() == null ? DataSyncTaskStatus.PUBLISHED : task.getStatus();
    }

    private void requireTaskStatus(DataSyncTaskEntity task, DataSyncTaskStatus expected, String detail) {
        if (taskStatus(task) != expected) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_TASK_STATUS, detail);
        }
    }

    private DataSyncTaskEntity requireTask(String workspaceId, String id) {
        if (StringUtils.isBlank(id)) throw new DataSyncException(DataSyncErrorCode.TASK_NOT_FOUND);
        return taskRepository
                .queryById(workspaceId, id)
                .orElseThrow(() -> new DataSyncException(DataSyncErrorCode.TASK_NOT_FOUND));
    }

    private DataSyncException runtimeUnavailable() {
        return new DataSyncException(DataSyncErrorCode.EXECUTION_FAILED, "同步引擎尚未接入，当前无法运行同步任务");
    }
}
