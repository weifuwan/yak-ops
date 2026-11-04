package io.yak.ops.api.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

import io.yak.ops.api.service.impl.TaskDefinitionLogServiceImpl;
import io.yak.ops.dao.entity.WorkflowTaskRelationLog;
import io.yak.ops.dao.repository.TaskDefinitionLogDao;
import io.yak.ops.dao.repository.WorkflowTaskRelationLogDao;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TaskDefinitionLogServiceTest {

    @InjectMocks
    private TaskDefinitionLogServiceImpl taskDefinitionLogService;

    @Mock
    private WorkflowTaskRelationLogDao workflowTaskRelationLogDao;

    @Mock
    private TaskDefinitionLogDao taskDefinitionLogDao;

    private List<WorkflowTaskRelationLog> getProcessTaskRelationList() {
        WorkflowTaskRelationLog processTaskRelationLog1 = new WorkflowTaskRelationLog();
        processTaskRelationLog1.setPreTaskCode(0L);
        processTaskRelationLog1.setPostTaskCode(1L);
        processTaskRelationLog1.setPostTaskVersion(1);

        WorkflowTaskRelationLog processTaskRelationLog2 = new WorkflowTaskRelationLog();
        processTaskRelationLog2.setPreTaskCode(0L);
        processTaskRelationLog2.setPostTaskCode(1L);
        processTaskRelationLog2.setPostTaskVersion(2);

        return Arrays.asList(
                processTaskRelationLog1,
                processTaskRelationLog2);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDeleteTaskByWorkflowDefinitionCode() {
        when(workflowTaskRelationLogDao.queryByWorkflowDefinitionCode(1L)).thenReturn(Collections.emptyList());
        assertDoesNotThrow(() -> taskDefinitionLogService.deleteTaskByWorkflowDefinitionCode(1L));

        when(workflowTaskRelationLogDao.queryByWorkflowDefinitionCode(2L)).thenReturn(getProcessTaskRelationList());
        assertDoesNotThrow(() -> taskDefinitionLogService.deleteTaskByWorkflowDefinitionCode(2L));
        Mockito.verify(taskDefinitionLogDao).deleteByTaskDefinitionCodes(Mockito.anySet());
    }
}
