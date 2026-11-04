package io.yak.ops.dao.repository.impl;

import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.TaskGroupQueueStatus;
import io.yak.ops.dao.entity.TaskGroupQueue;
import io.yak.ops.dao.mapper.TaskGroupQueueMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.TaskGroupQueueDao;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.NonNull;

@Repository
public class TaskGroupQueueDaoImpl extends BaseDao<TaskGroupQueue, TaskGroupQueueMapper> implements TaskGroupQueueDao {

    public TaskGroupQueueDaoImpl(@NonNull TaskGroupQueueMapper taskGroupQueueMapper) {
        super(taskGroupQueueMapper);
    }

    @Override
    public void deleteByWorkflowInstanceIds(List<Integer> workflowInstanceIds) {
        if (CollectionUtils.isEmpty(workflowInstanceIds)) {
            return;
        }
        mybatisMapper.deleteByWorkflowInstanceIds(workflowInstanceIds);
    }

    @Override
    public List<TaskGroupQueue> queryAllInQueueTaskGroupQueue() {
        return mybatisMapper.queryAllTaskGroupQueueByInQueue(Flag.YES.getCode());
    }

    @Override
    public List<TaskGroupQueue> queryInQueueTaskGroupQueue(int minTaskGroupQueueId, int limit) {
        return mybatisMapper.queryInQueueTaskGroupQueue(Flag.YES.getCode(), minTaskGroupQueueId, limit);
    }

    @Override
    public List<TaskGroupQueue> queryAllInQueueTaskGroupQueueByGroupId(Integer taskGroupId) {
        return mybatisMapper.queryAllInQueueTaskGroupQueueByGroupId(taskGroupId, Flag.YES.getCode());
    }

    @Override
    public List<TaskGroupQueue> queryByTaskInstanceId(Integer taskInstanceId) {
        return mybatisMapper.queryByTaskInstanceId(taskInstanceId);
    }

    @Override
    public boolean acquireTaskGroupQueue(Integer id, Date updateTime) {
        return mybatisMapper.acquireTaskGroupQueue(
                id,
                TaskGroupQueueStatus.WAIT_QUEUE.getCode(),
                TaskGroupQueueStatus.ACQUIRE_SUCCESS.getCode(),
                Flag.YES.getCode(),
                Flag.NO.getCode(),
                updateTime) > 0;
    }

    @Override
    public boolean deleteByTaskInstanceIdAndStatus(Integer taskInstanceId, TaskGroupQueueStatus status) {
        return mybatisMapper.deleteByTaskInstanceIdAndStatus(taskInstanceId, status.getCode()) > 0;
    }

    @Override
    public List<TaskGroupQueue> queryAcquiredTaskGroupQueueByGroupId(Integer taskGroupId) {
        return mybatisMapper.queryUsingTaskGroupQueueByGroupId(
                taskGroupId,
                TaskGroupQueueStatus.ACQUIRE_SUCCESS.getCode(),
                Flag.YES.getCode(),
                Flag.NO.getCode());
    }

    @Override
    public int countUsingTaskGroupQueueByGroupId(Integer taskGroupId) {
        return mybatisMapper.countUsingTaskGroupQueueByGroupId(taskGroupId,
                TaskGroupQueueStatus.ACQUIRE_SUCCESS.getCode(),
                Flag.YES.ordinal(),
                Flag.NO.getCode());
    }

    @Override
    public List<TaskGroupQueue> queryWaitNotifyForceStartTaskGroupQueue(int minTaskGroupQueueId, int limit) {
        return mybatisMapper.queryWaitNotifyForceStartTaskGroupQueue(
                Flag.YES.getCode(),
                Flag.YES.getCode(),
                minTaskGroupQueueId,
                limit);
    }
}
