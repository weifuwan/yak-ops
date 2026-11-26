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
    public boolean insertIfAbsent(DataSyncRecoveryPoint point) {
        try {
            return mybatisMapper.insert(point) > 0;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean insertPending(DataSyncRecoveryPoint point) {
        return mybatisMapper.insert(point) > 0;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markAvailable(long id, String restorePath, Date completedAt) {
        DataSyncRecoveryPoint update = new DataSyncRecoveryPoint();
        update.setState("AVAILABLE");
        update.setRestorePath(restorePath);
        update.setCompletedAt(completedAt);
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
        DataSyncRecoveryPoint update = new DataSyncRecoveryPoint();
        update.setUsedByInstanceId(instanceId);
        return mybatisMapper.update(
                update,
                new UpdateWrapper<DataSyncRecoveryPoint>()
                        .eq("recovery_point_id", recoveryPointId)
                        .eq("state", "USED")
                        .eq("used_by_workflow_instance_id", workflowInstanceId)) > 0;
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

    @Override
    public boolean supersedePreviousCheckpoints(
            int taskInstanceId, String executionId, String recoveryPointId) {
        DataSyncRecoveryPoint update = new DataSyncRecoveryPoint();
        update.setState("SUPERSEDED");
        return mybatisMapper.update(
                update,
                new UpdateWrapper<DataSyncRecoveryPoint>()
                        .eq("task_instance_id", taskInstanceId)
                        .eq("execution_id", executionId)
                        .eq("recovery_type", "CHECKPOINT")
                        .eq("state", "AVAILABLE")
                        .ne("recovery_point_id", recoveryPointId)) > 0;
    }
}
