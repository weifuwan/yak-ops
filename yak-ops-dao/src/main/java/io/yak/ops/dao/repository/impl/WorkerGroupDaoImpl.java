package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.WorkerGroup;
import io.yak.ops.dao.mapper.WorkerGroupMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.WorkerGroupDao;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import lombok.NonNull;

@Repository
public class WorkerGroupDaoImpl extends BaseDao<WorkerGroup, WorkerGroupMapper> implements WorkerGroupDao {

    public WorkerGroupDaoImpl(@NonNull WorkerGroupMapper workerGroupMapper) {
        super(workerGroupMapper);
    }

    @Override
    public boolean deleteByWorkerGroupName(String workerGroupName) {
        int deleted = mybatisMapper.deleteByWorkerGroupName(workerGroupName);
        return deleted > 0;
    }

    @Override
    public List<String> queryAllWorkerGroupNames() {
        return mybatisMapper.queryAllWorkerGroup().stream()
                .map(WorkerGroup::getName)
                .collect(Collectors.toList());
    }

    @Override
    public List<WorkerGroup> queryAllWorkerGroup() {
        return mybatisMapper.queryAllWorkerGroup();
    }

    @Override
    public List<WorkerGroup> queryWorkerGroupByName(String name) {
        return mybatisMapper.queryWorkerGroupByName(name);
    }
}
