package io.yak.ops.dao.repository.datasync.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.DataSyncAttemptEntity;
import io.yak.ops.dao.mapper.datasync.DataSyncAttemptMapper;
import io.yak.ops.dao.repository.datasync.DataSyncAttemptRepository;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 使用 MyBatis-Plus 实现 Data Sync Attempt 历史查询、指标更新和状态迁移。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Repository
public class DataSyncAttemptRepositoryImpl extends BaseRepositoryImpl<DataSyncAttemptMapper, DataSyncAttemptEntity>
        implements DataSyncAttemptRepository {

    @Resource
    private DataSyncAttemptMapper attemptMapper;

    @Override
    protected DataSyncAttemptMapper mapper() {
        return attemptMapper;
    }

    @Override
    public List<DataSyncAttemptEntity> queryByExecution(String workspaceId, String executionId) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(executionId)) return List.of();
        return attemptMapper.selectList(Wrappers.<DataSyncAttemptEntity>lambdaQuery()
                .eq(DataSyncAttemptEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncAttemptEntity::getExecutionId, executionId)
                .orderByAsc(DataSyncAttemptEntity::getAttemptNo));
    }

    @Override
    public boolean updateMetrics(String workspaceId, String id, long readRows, long writeRows) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(id) || readRows < 0 || writeRows < 0) return false;
        DataSyncAttemptEntity update = new DataSyncAttemptEntity();
        update.setReadRows(readRows);
        update.setWriteRows(writeRows);
        update.initUpdate();
        return attemptMapper.update(
                        update,
                        Wrappers.<DataSyncAttemptEntity>lambdaUpdate()
                                .eq(DataSyncAttemptEntity::getWorkspaceId, workspaceId)
                                .eq(DataSyncAttemptEntity::getId, id)
                                .eq(DataSyncAttemptEntity::getStatus, DataSyncAttemptStatus.RUNNING))
                > 0;
    }

    @Override
    public boolean transitionStatus(
            String workspaceId,
            String id,
            DataSyncAttemptStatus expectedStatus,
            DataSyncAttemptStatus targetStatus,
            LocalDateTime startTime,
            LocalDateTime finishTime,
            Integer errorCode,
            String errorMessage) {
        if (StringUtils.isBlank(workspaceId)
                || StringUtils.isBlank(id)
                || expectedStatus == null
                || targetStatus == null) {
            return false;
        }
        DataSyncAttemptEntity update = new DataSyncAttemptEntity();
        update.setStatus(targetStatus);
        update.setStartTime(startTime);
        update.setFinishTime(finishTime);
        update.setErrorCode(errorCode);
        update.setErrorMessage(errorMessage);
        update.initUpdate();
        return attemptMapper.update(
                        update,
                        Wrappers.<DataSyncAttemptEntity>lambdaUpdate()
                                .eq(DataSyncAttemptEntity::getWorkspaceId, workspaceId)
                                .eq(DataSyncAttemptEntity::getId, id)
                                .eq(DataSyncAttemptEntity::getStatus, expectedStatus))
                > 0;
    }

    @Override
    public int cancelActiveByExecution(String workspaceId, String executionId, LocalDateTime finishTime) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(executionId)) return 0;
        DataSyncAttemptEntity update = new DataSyncAttemptEntity();
        update.setStatus(DataSyncAttemptStatus.CANCELED);
        update.setFinishTime(finishTime == null ? DateUtils.now() : finishTime);
        update.initUpdate();
        return attemptMapper.update(
                update,
                Wrappers.<DataSyncAttemptEntity>lambdaUpdate()
                        .eq(DataSyncAttemptEntity::getWorkspaceId, workspaceId)
                        .eq(DataSyncAttemptEntity::getExecutionId, executionId)
                        .in(
                                DataSyncAttemptEntity::getStatus,
                                List.of(DataSyncAttemptStatus.PENDING, DataSyncAttemptStatus.RUNNING)));
    }

    @Override
    public int markActiveAsLost(LocalDateTime finishTime, Integer errorCode, String errorMessage) {
        DataSyncAttemptEntity update = new DataSyncAttemptEntity();
        update.setStatus(DataSyncAttemptStatus.LOST);
        update.setFinishTime(finishTime == null ? DateUtils.now() : finishTime);
        update.setErrorCode(errorCode);
        update.setErrorMessage(errorMessage);
        update.initUpdate();
        return attemptMapper.update(
                update,
                Wrappers.<DataSyncAttemptEntity>lambdaUpdate()
                        .in(
                                DataSyncAttemptEntity::getStatus,
                                List.of(DataSyncAttemptStatus.PENDING, DataSyncAttemptStatus.RUNNING)));
    }
}
