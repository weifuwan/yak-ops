package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.TaskGroup;
import io.yak.ops.dao.mapper.TaskGroupMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.TaskGroupDao;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class TaskGroupDaoImpl extends BaseDao<TaskGroup, TaskGroupMapper> implements TaskGroupDao {

    public TaskGroupDaoImpl(@NonNull TaskGroupMapper taskGroupMapper) {
        super(taskGroupMapper);
    }

    @Override
    public List<TaskGroup> queryAllTaskGroups() {
        return mybatisMapper.selectList(null);
    }

    @Override
    public List<TaskGroup> queryUsedTaskGroups() {
        return mybatisMapper.queryUsedTaskGroups();
    }

    @Override
    public List<TaskGroup> queryAvailableTaskGroups() {
        return mybatisMapper.queryAvailableTaskGroups();
    }

    @Override
    public boolean acquireTaskGroupSlot(Integer taskGroupId) {
        if (taskGroupId == null) {
            throw new IllegalArgumentException("taskGroupId cannot be null");
        }
        return mybatisMapper.acquireTaskGroupSlot(taskGroupId) > 0;
    }

    @Override
    public boolean releaseTaskGroupSlot(Integer taskGroupId) {
        if (taskGroupId == null) {
            throw new IllegalArgumentException("taskGroupId cannot be null");
        }
        return mybatisMapper.releaseTaskGroupSlot(taskGroupId) > 0;
    }
}
