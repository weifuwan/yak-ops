package io.yak.ops.dao.mapper;

import io.yak.ops.common.enums.TaskGroupQueueStatus;
import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.TaskGroupQueue;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TaskGroupQueueMapperTest extends BaseDaoTest {

    @Autowired
    TaskGroupQueueMapper taskGroupQueueMapper;

    int userId = 1;

    public TaskGroupQueue insertOne() {
        TaskGroupQueue taskGroupQueue = new TaskGroupQueue();
        taskGroupQueue.setTaskName("task1");
        taskGroupQueue.setGroupId(10);
        taskGroupQueue.setWorkflowInstanceId(11);
        taskGroupQueue.setPriority(10);
        taskGroupQueue.setStatus(TaskGroupQueueStatus.ACQUIRE_SUCCESS);
        Date date = new Date(System.currentTimeMillis());
        taskGroupQueue.setUpdateTime(date);
        taskGroupQueue.setUpdateTime(date);

        taskGroupQueueMapper.insert(taskGroupQueue);
        return taskGroupQueue;
    }

    /**
     * test update
     */
    @Test
    public void testUpdate() {
        TaskGroupQueue taskGroupQueue = insertOne();
        taskGroupQueue.setStatus(TaskGroupQueueStatus.ACQUIRE_SUCCESS);
        taskGroupQueue.setUpdateTime(new Date(System.currentTimeMillis()));
        int i = taskGroupQueueMapper.updateById(taskGroupQueue);
        Assertions.assertEquals(i, 1);
    }

    /**
     * test delete
     */
    @Test
    public void testDelete() {
        TaskGroupQueue taskGroupQueue = insertOne();
        int i = taskGroupQueueMapper.deleteByTaskId(taskGroupQueue.getId());
        Assertions.assertEquals(i, 0);
    }

    /**
     * test select
     */
    @Test
    public void testSelect() {
        TaskGroupQueue taskGroupQueue = insertOne();
        TaskGroupQueue result = taskGroupQueueMapper.selectById(taskGroupQueue.getId());
        Assertions.assertEquals(result.getTaskName(), "task1");

        List<TaskGroupQueue> taskGroupQueues = taskGroupQueueMapper.queryByStatus(taskGroupQueue.getStatus().getCode());
        Assertions.assertEquals(taskGroupQueues.size(), 1);

    }

    @Test
    public void testUpdateStatusByTaskId() {
        TaskGroupQueue taskGroupQueue = insertOne();
        int i = taskGroupQueueMapper.updateStatusByTaskId(taskGroupQueue.getTaskId(), 7);
        Assertions.assertEquals(i, 1);
    }

    @Test
    public void testDeleteByTaskId() {
        TaskGroupQueue taskGroupQueue = insertOne();
        int i = taskGroupQueueMapper.deleteByTaskId(taskGroupQueue.getTaskId());
        Assertions.assertEquals(i, 1);
    }
}
