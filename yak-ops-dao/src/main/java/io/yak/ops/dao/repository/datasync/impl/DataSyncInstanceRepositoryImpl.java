package io.yak.ops.dao.repository.datasync.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.mapper.datasync.DataSyncInstanceMapper;
import io.yak.ops.dao.repository.datasync.DataSyncInstancePageQuery;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 使用 MyBatis-Plus 实现 Workspace-scoped 数据同步实例分页与详情查询。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Repository
public class DataSyncInstanceRepositoryImpl extends BaseRepositoryImpl<DataSyncInstanceMapper, DataSyncInstanceEntity>
        implements DataSyncInstanceRepository {

    @Resource
    private DataSyncInstanceMapper instanceMapper;

    @Override
    protected DataSyncInstanceMapper mapper() {
        return instanceMapper;
    }

    @Override
    public PageData<DataSyncInstanceEntity> queryPage(String workspaceId, DataSyncInstancePageQuery query) {
        DataSyncInstancePageQuery condition =
                query == null ? new DataSyncInstancePageQuery(1, 10, null, null, null, null, null, null, null) : query;
        Page<DataSyncInstanceEntity> page = Page.of(Math.max(1, condition.pageNo()), Math.max(1, condition.pageSize()));
        IPage<DataSyncInstanceEntity> result = instanceMapper.selectPage(
                page,
                queryWrapper(workspaceId, condition)
                        .orderByDesc(DataSyncInstanceEntity::getCreateTime)
                        .orderByDesc(DataSyncInstanceEntity::getId));
        return new PageData<>(
                result.getRecords(), result.getTotal(), result.getPages(), result.getCurrent(), result.getSize());
    }

    @Override
    public Optional<DataSyncInstanceEntity> queryById(String workspaceId, String id) {
        if (!StringUtils.hasText(workspaceId) || !StringUtils.hasText(id)) return Optional.empty();
        return Optional.ofNullable(instanceMapper.selectOne(Wrappers.<DataSyncInstanceEntity>lambdaQuery()
                .eq(DataSyncInstanceEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncInstanceEntity::getTaskType, "DATA_SYNC")
                .eq(DataSyncInstanceEntity::getId, id)));
    }

    @Override
    public Optional<DataSyncInstanceEntity> queryLatestByTask(String workspaceId, String taskId) {
        if (!StringUtils.hasText(workspaceId) || !StringUtils.hasText(taskId)) return Optional.empty();
        return Optional.ofNullable(instanceMapper.selectOne(Wrappers.<DataSyncInstanceEntity>lambdaQuery()
                .eq(DataSyncInstanceEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncInstanceEntity::getTaskType, "DATA_SYNC")
                .eq(DataSyncInstanceEntity::getTaskId, taskId)
                .orderByDesc(DataSyncInstanceEntity::getCreateTime)
                .orderByDesc(DataSyncInstanceEntity::getId)
                .last("LIMIT 1")));
    }

    @Override
    public boolean existsActiveByTask(String workspaceId, String taskId) {
        if (!StringUtils.hasText(workspaceId) || !StringUtils.hasText(taskId)) return false;
        Long count = instanceMapper.selectCount(Wrappers.<DataSyncInstanceEntity>lambdaQuery()
                .eq(DataSyncInstanceEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncInstanceEntity::getTaskType, "DATA_SYNC")
                .eq(DataSyncInstanceEntity::getTaskId, taskId)
                .in(
                        DataSyncInstanceEntity::getStatus,
                        List.of(
                                DataSyncInstanceStatus.PENDING,
                                DataSyncInstanceStatus.RUNNING,
                                DataSyncInstanceStatus.RETRY_WAITING)));
        return count != null && count > 0;
    }

    @Override
    public List<DataSyncInstanceEntity> queryActive() {
        return instanceMapper.selectList(Wrappers.<DataSyncInstanceEntity>lambdaQuery()
                .eq(DataSyncInstanceEntity::getTaskType, "DATA_SYNC")
                .in(
                        DataSyncInstanceEntity::getStatus,
                        List.of(
                                DataSyncInstanceStatus.PENDING,
                                DataSyncInstanceStatus.RUNNING,
                                DataSyncInstanceStatus.RETRY_WAITING))
                .orderByAsc(DataSyncInstanceEntity::getCreateTime)
                .orderByAsc(DataSyncInstanceEntity::getId));
    }

    @Override
    public boolean updateMetrics(String workspaceId, String id, long readRows, long writeRows) {
        if (!StringUtils.hasText(workspaceId) || !StringUtils.hasText(id) || readRows < 0 || writeRows < 0) {
            return false;
        }
        DataSyncInstanceEntity update = new DataSyncInstanceEntity();
        update.setReadRows(readRows);
        update.setWriteRows(writeRows);
        update.initUpdate();
        return instanceMapper.update(
                        update,
                        Wrappers.<DataSyncInstanceEntity>lambdaUpdate()
                                .eq(DataSyncInstanceEntity::getWorkspaceId, workspaceId)
                                .eq(DataSyncInstanceEntity::getTaskType, "DATA_SYNC")
                                .eq(DataSyncInstanceEntity::getId, id)
                                .eq(DataSyncInstanceEntity::getStatus, DataSyncInstanceStatus.RUNNING))
                > 0;
    }

    @Override
    public boolean startAttempt(
            String workspaceId,
            String id,
            DataSyncInstanceStatus expectedStatus,
            int attemptNo,
            LocalDateTime startTime) {
        if (!StringUtils.hasText(workspaceId) || !StringUtils.hasText(id) || expectedStatus == null || attemptNo < 1) {
            return false;
        }
        DataSyncInstanceEntity update = new DataSyncInstanceEntity();
        update.setStatus(DataSyncInstanceStatus.RUNNING);
        update.setCurrentAttempt(attemptNo);
        update.setReadRows(0L);
        update.setWriteRows(0L);
        if (attemptNo == 1) update.setStartTime(startTime);
        update.initUpdate();
        return instanceMapper.update(
                        update,
                        Wrappers.<DataSyncInstanceEntity>lambdaUpdate()
                                .eq(DataSyncInstanceEntity::getWorkspaceId, workspaceId)
                                .eq(DataSyncInstanceEntity::getTaskType, "DATA_SYNC")
                                .eq(DataSyncInstanceEntity::getId, id)
                                .eq(DataSyncInstanceEntity::getStatus, expectedStatus)
                                .set(DataSyncInstanceEntity::getNextRetryTime, null)
                                .set(DataSyncInstanceEntity::getFinishTime, null)
                                .set(DataSyncInstanceEntity::getErrorCode, null)
                                .set(DataSyncInstanceEntity::getErrorMessage, null))
                > 0;
    }

    @Override
    public boolean waitForRetry(
            String workspaceId,
            String id,
            DataSyncInstanceStatus expectedStatus,
            int attemptNo,
            LocalDateTime nextRetryTime,
            long readRows,
            long writeRows,
            Integer errorCode,
            String errorMessage) {
        if (!StringUtils.hasText(workspaceId)
                || !StringUtils.hasText(id)
                || expectedStatus == null
                || attemptNo < 1
                || nextRetryTime == null) {
            return false;
        }
        DataSyncInstanceEntity update = new DataSyncInstanceEntity();
        update.setStatus(DataSyncInstanceStatus.RETRY_WAITING);
        update.setCurrentAttempt(attemptNo);
        update.setNextRetryTime(nextRetryTime);
        update.setReadRows(Math.max(0, readRows));
        update.setWriteRows(Math.max(0, writeRows));
        update.setErrorCode(errorCode);
        update.setErrorMessage(errorMessage);
        update.initUpdate();
        return instanceMapper.update(
                        update,
                        Wrappers.<DataSyncInstanceEntity>lambdaUpdate()
                                .eq(DataSyncInstanceEntity::getWorkspaceId, workspaceId)
                                .eq(DataSyncInstanceEntity::getTaskType, "DATA_SYNC")
                                .eq(DataSyncInstanceEntity::getId, id)
                                .eq(DataSyncInstanceEntity::getStatus, expectedStatus)
                                .set(DataSyncInstanceEntity::getFinishTime, null))
                > 0;
    }

    @Override
    public boolean completeExecution(
            String workspaceId,
            String id,
            DataSyncInstanceStatus expectedStatus,
            DataSyncInstanceStatus targetStatus,
            int attemptNo,
            LocalDateTime finishTime,
            long readRows,
            long writeRows,
            Integer errorCode,
            String errorMessage) {
        if (!StringUtils.hasText(workspaceId)
                || !StringUtils.hasText(id)
                || expectedStatus == null
                || targetStatus == null
                || attemptNo < 1) {
            return false;
        }
        DataSyncInstanceEntity update = new DataSyncInstanceEntity();
        update.setStatus(targetStatus);
        update.setCurrentAttempt(attemptNo);
        update.setFinishTime(finishTime == null ? DateUtils.now() : finishTime);
        update.setReadRows(Math.max(0, readRows));
        update.setWriteRows(Math.max(0, writeRows));
        update.setErrorCode(errorCode);
        update.setErrorMessage(errorMessage);
        update.initUpdate();
        return instanceMapper.update(
                        update,
                        Wrappers.<DataSyncInstanceEntity>lambdaUpdate()
                                .eq(DataSyncInstanceEntity::getWorkspaceId, workspaceId)
                                .eq(DataSyncInstanceEntity::getTaskType, "DATA_SYNC")
                                .eq(DataSyncInstanceEntity::getId, id)
                                .eq(DataSyncInstanceEntity::getStatus, expectedStatus)
                                .set(DataSyncInstanceEntity::getNextRetryTime, null))
                > 0;
    }

    @Override
    public boolean cancelExecution(
            String workspaceId, String id, DataSyncInstanceStatus expectedStatus, LocalDateTime finishTime) {
        if (!StringUtils.hasText(workspaceId) || !StringUtils.hasText(id) || expectedStatus == null) return false;
        DataSyncInstanceEntity update = new DataSyncInstanceEntity();
        update.setStatus(DataSyncInstanceStatus.CANCELED);
        update.setFinishTime(finishTime == null ? DateUtils.now() : finishTime);
        update.initUpdate();
        return instanceMapper.update(
                        update,
                        Wrappers.<DataSyncInstanceEntity>lambdaUpdate()
                                .eq(DataSyncInstanceEntity::getWorkspaceId, workspaceId)
                                .eq(DataSyncInstanceEntity::getTaskType, "DATA_SYNC")
                                .eq(DataSyncInstanceEntity::getId, id)
                                .eq(DataSyncInstanceEntity::getStatus, expectedStatus)
                                .set(DataSyncInstanceEntity::getNextRetryTime, null))
                > 0;
    }

    @Override
    public boolean transitionStatus(
            String workspaceId,
            String id,
            DataSyncInstanceStatus expectedStatus,
            DataSyncInstanceStatus targetStatus,
            LocalDateTime startTime,
            LocalDateTime finishTime,
            Integer errorCode,
            String errorMessage) {
        if (!StringUtils.hasText(workspaceId)
                || !StringUtils.hasText(id)
                || expectedStatus == null
                || targetStatus == null) {
            return false;
        }
        DataSyncInstanceEntity update = new DataSyncInstanceEntity();
        update.setStatus(targetStatus);
        update.setStartTime(startTime);
        update.setFinishTime(finishTime);
        update.setErrorCode(errorCode);
        update.setErrorMessage(errorMessage);
        update.initUpdate();
        int affected = instanceMapper.update(
                update,
                Wrappers.<DataSyncInstanceEntity>lambdaUpdate()
                        .eq(DataSyncInstanceEntity::getWorkspaceId, workspaceId)
                        .eq(DataSyncInstanceEntity::getTaskType, "DATA_SYNC")
                        .eq(DataSyncInstanceEntity::getId, id)
                        .eq(DataSyncInstanceEntity::getStatus, expectedStatus));
        return affected > 0;
    }

    @Override
    public int markActiveAsLost(LocalDateTime finishTime, Integer errorCode, String errorMessage) {
        DataSyncInstanceEntity update = new DataSyncInstanceEntity();
        update.setStatus(DataSyncInstanceStatus.LOST);
        update.setFinishTime(finishTime == null ? DateUtils.now() : finishTime);
        update.setErrorCode(errorCode);
        update.setErrorMessage(errorMessage);
        update.initUpdate();
        return instanceMapper.update(
                update,
                Wrappers.<DataSyncInstanceEntity>lambdaUpdate()
                        .eq(DataSyncInstanceEntity::getTaskType, "DATA_SYNC")
                        .in(
                                DataSyncInstanceEntity::getStatus,
                                List.of(DataSyncInstanceStatus.PENDING, DataSyncInstanceStatus.RUNNING))
                        .set(DataSyncInstanceEntity::getNextRetryTime, null));
    }

    private LambdaQueryWrapper<DataSyncInstanceEntity> queryWrapper(
            String workspaceId, DataSyncInstancePageQuery query) {
        LambdaQueryWrapper<DataSyncInstanceEntity> wrapper = Wrappers.<DataSyncInstanceEntity>lambdaQuery()
                .eq(DataSyncInstanceEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncInstanceEntity::getTaskType, "DATA_SYNC");
        return wrapper.eq(StringUtils.hasText(query.taskId()), DataSyncInstanceEntity::getTaskId, query.taskId())
                .like(StringUtils.hasText(query.keyword()), DataSyncInstanceEntity::getTaskName, query.keyword())
                .eq(query.syncType() != null, DataSyncInstanceEntity::getSyncType, query.syncType())
                .eq(query.status() != null, DataSyncInstanceEntity::getStatus, query.status())
                .eq(query.triggerType() != null, DataSyncInstanceEntity::getTriggerType, query.triggerType())
                .ge(query.startTimeStart() != null, DataSyncInstanceEntity::getStartTime, query.startTimeStart())
                .le(query.startTimeEnd() != null, DataSyncInstanceEntity::getStartTime, query.startTimeEnd());
    }
}
