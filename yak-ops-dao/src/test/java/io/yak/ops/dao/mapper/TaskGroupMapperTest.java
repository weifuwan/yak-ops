package io.yak.ops.dao.mapper;

import io.yak.ops.common.enums.Flag;
import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.TaskGroup;

import java.util.Date;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public class TaskGroupMapperTest extends BaseDaoTest {

    @Autowired
    TaskGroupMapper taskGroupMapper;

    /**
     * test insert
     */
    public TaskGroup insertOne() {
        TaskGroup taskGroup = new TaskGroup();
        taskGroup.setName("task group");
        taskGroup.setId(1);
        taskGroup.setUserId(1);
        taskGroup.setStatus(Flag.YES);
        taskGroup.setGroupSize(10);
        taskGroup.setDescription("this is a task group");
        Date date = new Date(System.currentTimeMillis());
        taskGroup.setUpdateTime(date);
        taskGroup.setUpdateTime(date);

        taskGroupMapper.insert(taskGroup);
        return taskGroup;
    }

    /**
     * test update
     */
    @Test
    public void testUpdate() {
        TaskGroup taskGroup = insertOne();
        taskGroup.setGroupSize(100);
        taskGroup.setUpdateTime(new Date(System.currentTimeMillis()));
        int i = taskGroupMapper.updateById(taskGroup);
        Assertions.assertEquals(i, 1);
    }

    /**
     * test CheckName
     */
    @Test
    public void testCheckName() {
        TaskGroup taskGroup = insertOne();
        TaskGroup result = taskGroupMapper.queryByName(taskGroup.getUserId(), taskGroup.getName());
        Assertions.assertNotNull(result);
    }

    /**
     * test queryTaskGroupPaging
     */
    @Test
    public void testQueryTaskGroupPaging() {
        TaskGroup taskGroup = insertOne();
        Page<TaskGroup> page = new Page(1, 3);
        IPage<TaskGroup> taskGroupIPage = taskGroupMapper.queryTaskGroupPaging(
                page,
                taskGroup.getName(), taskGroup.getStatus().getCode());

        Assertions.assertEquals(taskGroupIPage.getTotal(), 1);
    }
}
