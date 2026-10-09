package io.yak.ops.dao.repository.datasync.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.common.enums.datasync.DataSyncTableExecutionStatus;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.DataSyncTableExecutionEntity;
import io.yak.ops.dao.mapper.datasync.DataSyncTableExecutionMapper;
import io.yak.ops.dao.repository.datasync.DataSyncTableExecutionRepository;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 使用 MyBatis-Plus 实现 Workspace-scoped Table Execution 查询。
 *
 * @author weifuwan
 * @since 2026-10-07
 */
@Repository
public class DataSyncTableExecutionRepositoryImpl
        extends BaseRepositoryImpl<DataSyncTableExecutionMapper, DataSyncTableExecutionEntity>
        implements DataSyncTableExecutionRepository {

    @Resource
    private DataSyncTableExecutionMapper tableExecutionMapper;

    @Override
    protected DataSyncTableExecutionMapper mapper() {
        return tableExecutionMapper;
    }

    @Override
    public Optional<DataSyncTableExecutionEntity> queryById(String workspaceId, String id) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(id)) return Optional.empty();
        return Optional.ofNullable(tableExecutionMapper.selectOne(Wrappers.<DataSyncTableExecutionEntity>lambdaQuery()
                .eq(DataSyncTableExecutionEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncTableExecutionEntity::getId, id)));
    }

    @Override
    public boolean transition(
            String workspaceId,
            String id,
            DataSyncTableExecutionStatus expected,
            DataSyncTableExecutionStatus target,
            int attemptNo,
            long readRows,
            long writeRows,
            Integer errorCode,
            String errorMessage) {
        if (StringUtils.isBlank(workspaceId)
                || StringUtils.isBlank(id)
                || expected == null
                || target == null
                || attemptNo < 0
                || readRows < 0
                || writeRows < 0) return false;
        DataSyncTableExecutionEntity update = new DataSyncTableExecutionEntity();
        update.setStatus(target);
        update.setCurrentAttempt(attemptNo);
        update.setReadRows(readRows);
        update.setWriteRows(writeRows);
        if (target == DataSyncTableExecutionStatus.RUNNING && attemptNo == 1) update.setStartTime(DateUtils.now());
        if (target.isTerminal()) update.setFinishTime(DateUtils.now());
        if (errorCode != null) update.setErrorCode(errorCode);
        if (errorMessage != null) update.setErrorMessage(errorMessage);
        update.initUpdate();
        return tableExecutionMapper.update(
                        update,
                        Wrappers.<DataSyncTableExecutionEntity>lambdaUpdate()
                                .eq(DataSyncTableExecutionEntity::getWorkspaceId, workspaceId)
                                .eq(DataSyncTableExecutionEntity::getId, id)
                                .eq(DataSyncTableExecutionEntity::getStatus, expected)
                                .set(
                                        target == DataSyncTableExecutionStatus.RUNNING,
                                        DataSyncTableExecutionEntity::getFinishTime,
                                        null)
                                .set(
                                        target == DataSyncTableExecutionStatus.RUNNING,
                                        DataSyncTableExecutionEntity::getErrorCode,
                                        null)
                                .set(
                                        target == DataSyncTableExecutionStatus.RUNNING,
                                        DataSyncTableExecutionEntity::getErrorMessage,
                                        null))
                > 0;
    }

    @Override
    public boolean updateMetrics(String workspaceId, String id, int attemptNo, long readRows, long writeRows) {
        if (StringUtils.isBlank(workspaceId)
                || StringUtils.isBlank(id)
                || attemptNo < 1
                || readRows < 0
                || writeRows < 0) return false;
        DataSyncTableExecutionEntity update = new DataSyncTableExecutionEntity();
        update.setReadRows(readRows);
        update.setWriteRows(writeRows);
        update.initUpdate();
        return tableExecutionMapper.update(
                        update,
                        Wrappers.<DataSyncTableExecutionEntity>lambdaUpdate()
                                .eq(DataSyncTableExecutionEntity::getWorkspaceId, workspaceId)
                                .eq(DataSyncTableExecutionEntity::getId, id)
                                .eq(DataSyncTableExecutionEntity::getStatus, DataSyncTableExecutionStatus.RUNNING)
                                .eq(DataSyncTableExecutionEntity::getCurrentAttempt, attemptNo))
                > 0;
    }

    @Override
    public int finishUnfinished(
            String workspaceId,
            String executionId,
            DataSyncTableExecutionStatus terminal,
            LocalDateTime finishTime,
            Integer errorCode,
            String errorMessage) {
        if (StringUtils.isBlank(workspaceId)
                || StringUtils.isBlank(executionId)
                || terminal == null
                || !terminal.isTerminal()) return 0;
        DataSyncTableExecutionEntity update = new DataSyncTableExecutionEntity();
        update.setStatus(terminal);
        update.setFinishTime(finishTime == null ? DateUtils.now() : finishTime);
        update.setErrorCode(errorCode);
        update.setErrorMessage(errorMessage);
        update.initUpdate();
        return tableExecutionMapper.update(
                update,
                Wrappers.<DataSyncTableExecutionEntity>lambdaUpdate()
                        .eq(DataSyncTableExecutionEntity::getWorkspaceId, workspaceId)
                        .eq(DataSyncTableExecutionEntity::getExecutionId, executionId)
                        .in(
                                DataSyncTableExecutionEntity::getStatus,
                                List.of(
                                        DataSyncTableExecutionStatus.PLANNED,
                                        DataSyncTableExecutionStatus.PENDING,
                                        DataSyncTableExecutionStatus.RUNNING,
                                        DataSyncTableExecutionStatus.RETRY_WAITING)));
    }

    @Override
    public List<DataSyncTableExecutionEntity> queryByExecution(String workspaceId, String executionId) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(executionId)) return List.of();
        return tableExecutionMapper.selectList(Wrappers.<DataSyncTableExecutionEntity>lambdaQuery()
                .eq(DataSyncTableExecutionEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncTableExecutionEntity::getExecutionId, executionId)
                .orderByAsc(DataSyncTableExecutionEntity::getRouteOrder)
                .orderByAsc(DataSyncTableExecutionEntity::getId));
    }
}
