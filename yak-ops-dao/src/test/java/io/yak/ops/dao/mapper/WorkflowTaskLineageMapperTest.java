package io.yak.ops.dao.mapper;

import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.ReleaseState;
import io.yak.ops.common.enums.WarningType;
import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.Schedule;
import io.yak.ops.dao.entity.WorkFlowRelationDetail;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowTaskLineage;
import io.yak.ops.dao.entity.WorkflowTaskRelation;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class WorkflowTaskLineageMapperTest extends BaseDaoTest {

    @Autowired
    private WorkflowTaskLineageMapper workflowTaskLineageMapper;

    @Autowired
    private WorkflowDefinitionMapper workflowDefinitionMapper;

    @Autowired
    private ScheduleMapper scheduleMapper;

    @Autowired
    private WorkflowTaskRelationMapper workflowTaskRelationMapper;

    /**
     * insert
     */
    private void insertOneProcessTaskRelation() {
        // insertOne
        WorkflowTaskRelation workflowTaskRelation = new WorkflowTaskRelation();
        workflowTaskRelation.setName("def 1");

        workflowTaskRelation.setProjectCode(1L);
        workflowTaskRelation.setWorkflowDefinitionCode(1L);
        workflowTaskRelation.setPostTaskCode(3L);
        workflowTaskRelation.setPostTaskVersion(1);
        workflowTaskRelation.setPreTaskCode(2L);
        workflowTaskRelation.setPreTaskVersion(1);
        workflowTaskRelation.setUpdateTime(new Date());
        workflowTaskRelation.setCreateTime(new Date());
        workflowTaskRelationMapper.insert(workflowTaskRelation);
    }

    /**
     * insert
     *
     */
    private void insertOneProcessDefinition() {
        // insertOne
        WorkflowDefinition workflowDefinition = new WorkflowDefinition();
        workflowDefinition.setCode(1L);
        workflowDefinition.setName("def 1");
        workflowDefinition.setProjectCode(1L);
        workflowDefinition.setUserId(101);
        workflowDefinition.setUpdateTime(new Date());
        workflowDefinition.setCreateTime(new Date());
        workflowDefinitionMapper.insert(workflowDefinition);
    }

    private void insertOneProcessLineage() {
        // insertOne
        WorkflowTaskLineage workflowTaskLineage = new WorkflowTaskLineage();
        workflowTaskLineage.setWorkflowDefinitionCode(1L);
        workflowTaskLineage.setWorkflowDefinitionVersion(1);
        workflowTaskLineage.setTaskDefinitionCode(1L);
        workflowTaskLineage.setTaskDefinitionVersion(1);
        workflowTaskLineage.setDeptProjectCode(1L);
        workflowTaskLineage.setDeptWorkflowDefinitionCode(1L);
        workflowTaskLineage.setDeptTaskDefinitionCode(1L);
        workflowTaskLineage.setUpdateTime(new Date());
        workflowTaskLineage.setCreateTime(new Date());
        workflowTaskLineageMapper.insert(workflowTaskLineage);
    }

    /**
     * insert
     *
     */
    private void insertOneSchedule(int id) {
        // insertOne
        Schedule schedule = new Schedule();
        schedule.setStartTime(new Date());
        schedule.setEndTime(new Date());
        schedule.setCrontab("");
        schedule.setFailureStrategy(FailureStrategy.CONTINUE);
        schedule.setReleaseState(ReleaseState.OFFLINE);
        schedule.setWarningType(WarningType.NONE);
        schedule.setCreateTime(new Date());
        schedule.setUpdateTime(new Date());
        schedule.setWorkflowDefinitionCode(id);
        scheduleMapper.insert(schedule);
    }

    @Test
    public void testQueryWorkFlowLineageByName() {
        insertOneProcessDefinition();
        WorkflowDefinition workflowDefinition = workflowDefinitionMapper.queryByCode(1L);
        insertOneSchedule(workflowDefinition.getId());
        List<WorkFlowRelationDetail> workFlowLineages = workflowTaskLineageMapper
                .queryWorkFlowLineageByName(workflowDefinition.getProjectCode(), workflowDefinition.getName());
        Assertions.assertNotEquals(0, workFlowLineages.size());
    }

    @Test
    public void testQueryWorkFlowLineage() {
        insertOneProcessDefinition();
        WorkflowDefinition workflowDefinition = workflowDefinitionMapper.queryByCode(1L);
        insertOneProcessTaskRelation();
        insertOneProcessLineage();
        List<WorkflowTaskLineage> workflowTaskLineages =
                workflowTaskLineageMapper.queryByProjectCode(workflowDefinition.getProjectCode());
        Assertions.assertNotEquals(0, workflowTaskLineages.size());
    }

    @Test
    public void testQueryWorkFlowLineageByCode() {
        insertOneProcessDefinition();
        WorkflowDefinition workflowDefinition = workflowDefinitionMapper.queryByCode(1L);
        insertOneSchedule(workflowDefinition.getId());
        List<WorkFlowRelationDetail> workFlowLineages = workflowTaskLineageMapper
                .queryWorkFlowLineageByCode(workflowDefinition.getCode());
        Assertions.assertNotNull(workFlowLineages);
    }

}
