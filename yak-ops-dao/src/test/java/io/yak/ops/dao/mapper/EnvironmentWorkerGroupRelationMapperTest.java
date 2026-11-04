package io.yak.ops.dao.mapper;

import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.EnvironmentWorkerGroupRelation;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class EnvironmentWorkerGroupRelationMapperTest extends BaseDaoTest {

    @Autowired
    private EnvironmentWorkerGroupRelationMapper environmentWorkerGroupRelationMapper;

    @BeforeEach
    public void setUp() {
        clearTestData();
    }

    @AfterEach
    public void after() {
        clearTestData();
    }

    public void clearTestData() {
        environmentWorkerGroupRelationMapper.selectList(null).stream().forEach(environment -> {
            environmentWorkerGroupRelationMapper.deleteById(environment.getId());
        });
    }

    /**
     * insert
     *
     * @return ProcessDefinition
     */
    private EnvironmentWorkerGroupRelation insertOne() {
        // insertOne
        EnvironmentWorkerGroupRelation relation = new EnvironmentWorkerGroupRelation();
        relation.setEnvironmentCode(1L);
        relation.setWorkerGroup("default");
        relation.setOperator(1);
        relation.setUpdateTime(new Date());
        relation.setCreateTime(new Date());
        environmentWorkerGroupRelationMapper.insert(relation);
        return relation;
    }

    /**
     * test query
     */
    @Test
    public void testQuery() {
        insertOne();
        // query
        List<EnvironmentWorkerGroupRelation> relations = environmentWorkerGroupRelationMapper.selectList(null);
        Assertions.assertEquals(relations.size(), 1);
    }

    @Test
    public void testQueryByEnvironmentCode() {
        EnvironmentWorkerGroupRelation relation = insertOne();
        List<EnvironmentWorkerGroupRelation> environmentWorkerGroupRelations =
                environmentWorkerGroupRelationMapper.queryByEnvironmentCode(1L);
        Assertions.assertNotEquals(0, environmentWorkerGroupRelations.size());
    }

    @Test
    public void testQueryByWorkerGroupName() {
        EnvironmentWorkerGroupRelation relation = insertOne();
        List<EnvironmentWorkerGroupRelation> environmentWorkerGroupRelations =
                environmentWorkerGroupRelationMapper.queryByWorkerGroupName("default");
        Assertions.assertNotEquals(0, environmentWorkerGroupRelations.size());
    }

    @Test
    public void testDeleteByCode() {
        EnvironmentWorkerGroupRelation relation = insertOne();
        int i = environmentWorkerGroupRelationMapper.deleteByCode(1L, "default");
        Assertions.assertNotEquals(0, i);
    }
}
