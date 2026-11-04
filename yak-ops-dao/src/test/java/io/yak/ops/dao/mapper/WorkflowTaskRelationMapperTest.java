package io.yak.ops.dao.mapper;

import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.WorkflowTaskRelation;

import java.util.Date;
import java.util.List;

import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class WorkflowTaskRelationMapperTest extends BaseDaoTest {

    @Autowired
    private WorkflowTaskRelationMapper workflowTaskRelationMapper;

    /**
     * insert
     *
     * @return ProcessDefinition
     */
    private WorkflowTaskRelation insertOne() {
        // insertOne
        WorkflowTaskRelation workflowTaskRelation = new WorkflowTaskRelation();
        workflowTaskRelation.setName("def 1");

        workflowTaskRelation.setProjectCode(1L);
        workflowTaskRelation.setWorkflowDefinitionCode(1L);
        workflowTaskRelation.setPostTaskCode(3L);
        workflowTaskRelation.setPreTaskCode(2L);
        workflowTaskRelation.setUpdateTime(new Date());
        workflowTaskRelation.setCreateTime(new Date());
        workflowTaskRelationMapper.insert(workflowTaskRelation);
        return workflowTaskRelation;
    }

    @Test
    public void testQueryByWorkflowDefinitionCode() {
        WorkflowTaskRelation workflowTaskRelation = insertOne();
        List<WorkflowTaskRelation> workflowTaskRelations = workflowTaskRelationMapper.queryByWorkflowDefinitionCode(1L);
        Assertions.assertNotEquals(0, workflowTaskRelations.size());
    }

    @Test
    public void testQueryByTaskCode() {
        WorkflowTaskRelation workflowTaskRelation = insertOne();
        List<WorkflowTaskRelation> workflowTaskRelations = workflowTaskRelationMapper.queryByTaskCode(2L);
        Assertions.assertNotEquals(0, workflowTaskRelations.size());
    }

    @Test
    public void testQueryByTaskCodes() {
        WorkflowTaskRelation workflowTaskRelation = insertOne();

        Long[] codes = Arrays.array(1L, 2L);
        List<WorkflowTaskRelation> workflowTaskRelations = workflowTaskRelationMapper.queryByTaskCodes(codes);
        Assertions.assertNotEquals(0, workflowTaskRelations.size());
    }

    @Test
    public void testDeleteByWorkflowDefinitionCode() {
        WorkflowTaskRelation workflowTaskRelation = insertOne();
        int i = workflowTaskRelationMapper.deleteByWorkflowDefinitionCode(1L, 1L);
        Assertions.assertNotEquals(0, i);
    }

}
