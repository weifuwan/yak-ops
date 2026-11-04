package io.yak.ops.dao.mapper;

import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.WorkerGroup;

import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WorkerGroupMapperTest extends BaseDaoTest {

    @Resource
    private WorkerGroupMapper workerGroupMapper;

    @BeforeEach
    public void setUp() {
        clearTestData();
    }

    @AfterEach
    public void after() {
        clearTestData();
    }

    /**
     * clear all test data
     */
    public void clearTestData() {
        workerGroupMapper.queryAllWorkerGroup()
                .forEach(workerGroup -> workerGroupMapper.deleteById(workerGroup.getId()));
    }

    /**
     * insert a worker group into DB
     *
     * @return worker group
     */
    private WorkerGroup insertOneWorkerGroup() {
        WorkerGroup workerGroup = new WorkerGroup();
        workerGroup.setName("Server1");
        workerGroup.setDescription("Server1");
        workerGroup.setCreateTime(new Date());
        workerGroup.setUpdateTime(new Date());
        workerGroup.setSystemDefault(true);
        workerGroup.setAddrList("localhost");
        workerGroupMapper.insert(workerGroup);
        return workerGroup;
    }

    /**
     * test query all worker groups
     */
    @Test
    public void testQueryAllWorkerGroups() {
        insertOneWorkerGroup();
        List<WorkerGroup> workerGroups = workerGroupMapper.queryAllWorkerGroup();
        Assertions.assertEquals(1, workerGroups.size());
    }

    /**
     * test query worker group by name
     */
    @Test
    public void testQueryWorkerGroupByName() {
        WorkerGroup workerGroup = insertOneWorkerGroup();
        List<WorkerGroup> workerGroups = workerGroupMapper.queryWorkerGroupByName(workerGroup.getName());
        Assertions.assertEquals(1, workerGroups.size());

        workerGroups = workerGroupMapper.queryWorkerGroupByName("server2");
        Assertions.assertEquals(0, workerGroups.size());
    }

    /**
     * test update workerGroup
     */
    @Test
    public void testUpdate() {
        WorkerGroup workerGroup = insertOneWorkerGroup();
        workerGroup.setDescription("Server Update");
        int update = workerGroupMapper.updateById(workerGroup);
        Assertions.assertEquals(1, update);
    }

    /**
     * test delete workerGroup
     */
    @Test
    public void delete() {
        WorkerGroup workerGroup = insertOneWorkerGroup();
        int delete = workerGroupMapper.deleteById(workerGroup.getId());
        Assertions.assertEquals(1, delete);
    }
}
