package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.DataSyncRecoveryPoint;
import io.yak.ops.dao.mapper.DataSyncRecoveryPointMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.DataSyncRecoveryPointDao;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import lombok.NonNull;

@Repository
public class DataSyncRecoveryPointDaoImpl
        extends BaseDao<DataSyncRecoveryPoint, DataSyncRecoveryPointMapper>
        implements DataSyncRecoveryPointDao {

    public DataSyncRecoveryPointDaoImpl(@NonNull DataSyncRecoveryPointMapper mapper) {
        super(mapper);
    }

    @Override
    public DataSyncRecoveryPoint queryByTaskInstanceId(int taskInstanceId) {
        DataSyncRecoveryPoint available = queryLatestAvailableByTaskInstanceId(taskInstanceId);
        if (available != null) {
            return available;
        }
        return mybatisMapper.selectOne(
                new QueryWrapper<DataSyncRecoveryPoint>()
                        .eq("task_instance_id", taskInstanceId)
                        .orderByDesc("completed_at")
                        .orderByDesc("created_at")
                        .last("LIMIT 1"));
    }

    @Override
    public DataSyncRecoveryPoint queryLatestAvailableByTaskInstanceId(int taskInstanceId) {
        return mybatisMapper.selectOne(
                new QueryWrapper<DataSyncRecoveryPoint>()
                        .eq("task_instance_id", taskInstanceId)
                        .eq("state", "AVAILABLE")
                        .isNotNull("restore_path")
                        .ne("restore_path", "")
                        .orderByDesc("completed_at")
                        .orderByDesc("created_at")
                        .last("LIMIT 1"));
    }

    @Override
    public List<DataSyncRecoveryPoint> queryByTaskInstanceIds(List<Integer> taskInstanceIds) {
        if (CollectionUtils.isEmpty(taskInstanceIds)) {
            return Collections.emptyList();
        }
        return mybatisMapper.selectList(
                new QueryWrapper<DataSyncRecoveryPoint>()
                        .in("task_instance_id", taskInstanceIds)
                        .orderByDesc("completed_at")
                        .orderByDesc("created_at"));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean saveCheckpointIfAbsent(DataSyncRecoveryPoint point) {
        Long existingCount = mybatisMapper.selectCount(
                new QueryWrapper<DataSyncRecoveryPoint>()
                        .eq("recovery_point_id", point.getRecoveryPointId()));
        if (existingCount != null && existingCount > 0) {
            return false;
        }
        List<DataSyncRecoveryPoint> retained = mybatisMapper.selectList(
                new QueryWrapper<DataSyncRecoveryPoint>()
                        .eq("task_instance_id", point.getTaskInstanceId())
                        .eq("execution_id", point.getExecutionId())
                        .eq("recovery_type", "CHECKPOINT")
                        .eq("state", "AVAILABLE")
                        .orderByDesc("completed_at")
                        .last("LIMIT 3"));
        if (retained.size() >= 3 && !isNewerCheckpoint(point, retained.get(retained.size() - 1))) {
            return false;
        }
        point.setState("AVAILABLE");
        try {
            if (mybatisMapper.insert(point) <= 0) {
                return false;
            }
        } catch (DuplicateKeyException duplicate) {
            return false;
        }

        List<DataSyncRecoveryPoint> afterInsert = mybatisMapper.selectList(
                new QueryWrapper<DataSyncRecoveryPoint>()
                        .eq("task_instance_id", point.getTaskInstanceId())
                        .eq("execution_id", point.getExecutionId())
                        .eq("recovery_type", "CHECKPOINT")
                        .eq("state", "AVAILABLE")
                        .orderByDesc("completed_at"));
        for (int index = 3; index < afterInsert.size(); index++) {
            mybatisMapper.deleteById(afterInsert.get(index).getId());
        }
        return true;
    }

    private static boolean isNewerCheckpoint(DataSyncRecoveryPoint candidate, DataSyncRecoveryPoint latest) {
        if (candidate.getCompletedAt() == null) {
            return false;
        }
        if (latest.getCompletedAt() == null) {
            return true;
        }
        int timeComparison = candidate.getCompletedAt().compareTo(latest.getCompletedAt());
        if (timeComparison != 0) {
            return timeComparison > 0;
        }
        try {
            return Long.parseLong(candidate.getCheckpointId()) > Long.parseLong(latest.getCheckpointId());
        } catch (NumberFormatException exception) {
            return candidate.getRecoveryPointId().compareTo(latest.getRecoveryPointId()) > 0;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean insertPending(DataSyncRecoveryPoint point) {
        return mybatisMapper.insert(point) > 0;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markAvailable(
            long id, String restorePath, Date completedAt,
            String executionId, String jobId, String runtimeMode) {
        DataSyncRecoveryPoint update = new DataSyncRecoveryPoint();
        update.setState("AVAILABLE");
        update.setRestorePath(restorePath);
        update.setCompletedAt(completedAt);
        update.setExecutionId(executionId);
        update.setJobId(jobId);
        update.setRuntimeMode(runtimeMode);
        return mybatisMapper.update(
                update,
                new UpdateWrapper<DataSyncRecoveryPoint>()
                        .eq("id", id)
                        .eq("state", "CREATING")) > 0;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markFailed(long id) {
        DataSyncRecoveryPoint update = new DataSyncRecoveryPoint();
        update.setState("FAILED");
        return mybatisMapper.update(
                update,
                new UpdateWrapper<DataSyncRecoveryPoint>()
                        .eq("id", id)
                        .eq("state", "CREATING")) > 0;
    }

    @Override
    public boolean markUsed(long id, int workflowInstanceId) {
        DataSyncRecoveryPoint update = new DataSyncRecoveryPoint();
        update.setState("USED");
        update.setUsedByWorkflowInstanceId(workflowInstanceId);
        update.setUsedAt(new Date());
        return mybatisMapper.update(
                update,
                new UpdateWrapper<DataSyncRecoveryPoint>()
                        .eq("id", id)
                        .eq("state", "AVAILABLE")) > 0;
    }

    @Override
    public boolean markUsedByInstance(String recoveryPointId, int instanceId, int workflowInstanceId) {
        QueryWrapper<DataSyncRecoveryPoint> alreadyLinked = new QueryWrapper<DataSyncRecoveryPoint>()
                .eq("recovery_point_id", recoveryPointId)
                .eq("state", "USED")
                .eq("used_by_workflow_instance_id", workflowInstanceId)
                .eq("used_by_instance_id", instanceId);
        if (mybatisMapper.selectCount(alreadyLinked) > 0) {
            return true;
        }

        DataSyncRecoveryPoint update = new DataSyncRecoveryPoint();
        update.setUsedByInstanceId(instanceId);
        return mybatisMapper.update(
                update,
                new UpdateWrapper<DataSyncRecoveryPoint>()
                        .eq("recovery_point_id", recoveryPointId)
                        .eq("state", "USED")
                        .eq("used_by_workflow_instance_id", workflowInstanceId)
                        .isNull("used_by_instance_id")) > 0;
    }

    @Override
    public boolean markSuperseded(long id) {
        DataSyncRecoveryPoint update = new DataSyncRecoveryPoint();
        update.setState("SUPERSEDED");
        return mybatisMapper.update(
                update,
                new UpdateWrapper<DataSyncRecoveryPoint>()
                        .eq("id", id)
                        .eq("state", "AVAILABLE")) > 0;
    }

    @Override
    public boolean markAllAvailableSuperseded(int taskInstanceId) {
        DataSyncRecoveryPoint update = new DataSyncRecoveryPoint();
        update.setState("SUPERSEDED");
        return mybatisMapper.update(
                update,
                new UpdateWrapper<DataSyncRecoveryPoint>()
                        .eq("task_instance_id", taskInstanceId)
                        .eq("state", "AVAILABLE")) > 0;
    }
}
