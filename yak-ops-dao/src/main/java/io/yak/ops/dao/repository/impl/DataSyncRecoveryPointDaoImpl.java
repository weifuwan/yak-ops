package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.DataSyncRecoveryPoint;
import io.yak.ops.dao.mapper.DataSyncRecoveryPointMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.DataSyncRecoveryPointDao;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Repository;

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
                        .eq("task_instance_id", taskInstanceId));
    }

    @Override
    public List<DataSyncRecoveryPoint> queryAvailableByTaskInstanceIds(List<Integer> taskInstanceIds) {
        if (CollectionUtils.isEmpty(taskInstanceIds)) {
            return Collections.emptyList();
        }
        return mybatisMapper.selectList(
                new QueryWrapper<DataSyncRecoveryPoint>()
                        .in("task_instance_id", taskInstanceIds)
                        .eq("state", "AVAILABLE")
                        .orderByDesc("created_at"));
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
    public boolean markSuperseded(long id) {
        DataSyncRecoveryPoint update = new DataSyncRecoveryPoint();
        update.setState("SUPERSEDED");
        return mybatisMapper.update(
                update,
                new UpdateWrapper<DataSyncRecoveryPoint>()
                        .eq("id", id)
                        .eq("state", "AVAILABLE")) > 0;
    }
}
