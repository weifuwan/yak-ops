package io.yak.ops.dao.repository.datasync.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.DataSyncTableAttemptEntity;
import io.yak.ops.dao.mapper.datasync.DataSyncTableAttemptMapper;
import io.yak.ops.dao.repository.datasync.DataSyncTableAttemptRepository;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 单表 Attempt 历史的 Workspace-scoped 读写和条件状态迁移。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
@Repository
public class DataSyncTableAttemptRepositoryImpl
        extends BaseRepositoryImpl<DataSyncTableAttemptMapper, DataSyncTableAttemptEntity>
        implements DataSyncTableAttemptRepository {

    @Resource
    private DataSyncTableAttemptMapper tableAttemptMapper;

    @Override
    protected DataSyncTableAttemptMapper mapper() {
        return tableAttemptMapper;
    }

    @Override
    public List<DataSyncTableAttemptEntity> queryByTableExecution(String workspaceId, String tableExecutionId) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(tableExecutionId)) return List.of();
        return tableAttemptMapper.selectList(Wrappers.<DataSyncTableAttemptEntity>lambdaQuery()
                .eq(DataSyncTableAttemptEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncTableAttemptEntity::getTableExecutionId, tableExecutionId)
                .orderByAsc(DataSyncTableAttemptEntity::getAttemptNo));
    }

    @Override
    public boolean transition(
            String workspaceId,
            String id,
            DataSyncAttemptStatus expected,
            DataSyncAttemptStatus target,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            Integer errorCode,
            String message) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(id) || expected == null || target == null) {
            return false;
        }
        DataSyncTableAttemptEntity update = new DataSyncTableAttemptEntity();
        update.setStatus(target);
        if (startedAt != null) update.setStartTime(startedAt);
        if (finishedAt != null) update.setFinishTime(finishedAt);
        if (errorCode != null) update.setErrorCode(errorCode);
        if (message != null) update.setErrorMessage(message);
        update.initUpdate();
        return tableAttemptMapper.update(
                        update,
                        Wrappers.<DataSyncTableAttemptEntity>lambdaUpdate()
                                .eq(DataSyncTableAttemptEntity::getWorkspaceId, workspaceId)
                                .eq(DataSyncTableAttemptEntity::getId, id)
                                .eq(DataSyncTableAttemptEntity::getStatus, expected))
                > 0;
    }

    @Override
    public boolean updateMetrics(String workspaceId, String id, long readRows, long writeRows) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(id) || readRows < 0 || writeRows < 0) return false;
        DataSyncTableAttemptEntity update = new DataSyncTableAttemptEntity();
        update.setReadRows(readRows);
        update.setWriteRows(writeRows);
        update.initUpdate();
        return tableAttemptMapper.update(
                        update,
                        Wrappers.<DataSyncTableAttemptEntity>lambdaUpdate()
                                .eq(DataSyncTableAttemptEntity::getWorkspaceId, workspaceId)
                                .eq(DataSyncTableAttemptEntity::getId, id)
                                .eq(DataSyncTableAttemptEntity::getStatus, DataSyncAttemptStatus.RUNNING))
                > 0;
    }

    @Override
    public int cancelActive(String workspaceId, String tableExecutionId, LocalDateTime finishedAt) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(tableExecutionId)) return 0;
        DataSyncTableAttemptEntity update = new DataSyncTableAttemptEntity();
        update.setStatus(DataSyncAttemptStatus.CANCELED);
        update.setFinishTime(finishedAt == null ? DateUtils.now() : finishedAt);
        update.initUpdate();
        return tableAttemptMapper.update(
                update,
                Wrappers.<DataSyncTableAttemptEntity>lambdaUpdate()
                        .eq(DataSyncTableAttemptEntity::getWorkspaceId, workspaceId)
                        .eq(DataSyncTableAttemptEntity::getTableExecutionId, tableExecutionId)
                        .in(
                                DataSyncTableAttemptEntity::getStatus,
                                List.of(DataSyncAttemptStatus.PENDING, DataSyncAttemptStatus.RUNNING)));
    }

    @Override
    public int markActiveAsLost(
            String workspaceId, String tableExecutionId, LocalDateTime finishedAt, Integer errorCode, String message) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(tableExecutionId)) return 0;
        DataSyncTableAttemptEntity update = new DataSyncTableAttemptEntity();
        update.setStatus(DataSyncAttemptStatus.LOST);
        update.setFinishTime(finishedAt == null ? DateUtils.now() : finishedAt);
        update.setErrorCode(errorCode);
        update.setErrorMessage(message);
        update.initUpdate();
        return tableAttemptMapper.update(
                update,
                Wrappers.<DataSyncTableAttemptEntity>lambdaUpdate()
                        .eq(DataSyncTableAttemptEntity::getWorkspaceId, workspaceId)
                        .eq(DataSyncTableAttemptEntity::getTableExecutionId, tableExecutionId)
                        .in(
                                DataSyncTableAttemptEntity::getStatus,
                                List.of(DataSyncAttemptStatus.PENDING, DataSyncAttemptStatus.RUNNING)));
    }

    @Override
    public int markActiveAsLost(LocalDateTime finishedAt, Integer errorCode, String message) {
        DataSyncTableAttemptEntity update = new DataSyncTableAttemptEntity();
        update.setStatus(DataSyncAttemptStatus.LOST);
        update.setFinishTime(finishedAt == null ? DateUtils.now() : finishedAt);
        update.setErrorCode(errorCode);
        update.setErrorMessage(message);
        update.initUpdate();
        return tableAttemptMapper.update(
                update,
                Wrappers.<DataSyncTableAttemptEntity>lambdaUpdate()
                        .in(
                                DataSyncTableAttemptEntity::getStatus,
                                List.of(DataSyncAttemptStatus.PENDING, DataSyncAttemptStatus.RUNNING)));
    }
}
