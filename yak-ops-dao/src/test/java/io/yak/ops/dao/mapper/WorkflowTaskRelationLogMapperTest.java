package io.yak.ops.dao.mapper;

import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.WorkflowTaskRelationLog;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class WorkflowTaskRelationLogMapperTest extends BaseDaoTest {

    @Autowired
    private WorkflowTaskRelationLogMapper workflowTaskRelationLogMapper;

    /**
     * insert
     *
     * @return ProcessDefinition
     */
    private WorkflowTaskRelationLog insertOne() {
        // insertOne
        WorkflowTaskRelationLog processTaskRelationLog = new WorkflowTaskRelationLog();
        processTaskRelationLog.setName("def 1");
        processTaskRelationLog.setWorkflowDefinitionVersion(1);
        processTaskRelationLog.setProjectCode(1L);
        processTaskRelationLog.setWorkflowDefinitionCode(1L);
        processTaskRelationLog.setPostTaskCode(3L);
        processTaskRelationLog.setPreTaskCode(2L);
        processTaskRelationLog.setUpdateTime(new Date());
        processTaskRelationLog.setCreateTime(new Date());
        workflowTaskRelationLogMapper.insert(processTaskRelationLog);
        return processTaskRelationLog;
    }

    @Test
    public void testQueryByWorkflowCodeAndVersion() {
        WorkflowTaskRelationLog processTaskRelationLog = insertOne();
        List<WorkflowTaskRelationLog> processTaskRelationLogs = workflowTaskRelationLogMapper
                .queryByWorkflowCodeAndVersion(1L, 1);
        Assertions.assertNotEquals(0, processTaskRelationLogs.size());
    }

}
