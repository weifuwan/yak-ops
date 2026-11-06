package io.yak.ops.api.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.api.dto.datasync.DataSyncTaskRequest;
import io.yak.ops.api.service.ExecutorService;
import io.yak.ops.api.service.ProjectService;
import io.yak.ops.api.service.WorkflowDefinitionService;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.DataSourceDao;
import io.yak.ops.dao.repository.TaskDefinitionDao;
import io.yak.ops.dao.repository.TaskDefinitionLogDao;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.dao.repository.WorkflowDefinitionDao;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.dao.repository.WorkflowTaskRelationDao;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.spi.enums.DbType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@ExtendWith(MockitoExtension.class)
class DataSyncServiceImplTest {

    private static final long PROJECT_CODE = 1001L;

    @Mock
    private ProjectService projectService;
    @Mock
    private DataSourceDao dataSourceDao;
    @Mock
    private TaskDefinitionDao taskDefinitionDao;
    @Mock
    private TaskDefinitionLogDao taskDefinitionLogDao;
    @Mock
    private TaskInstanceDao taskInstanceDao;
    @Mock
    private WorkflowDefinitionDao workflowDefinitionDao;
    @Mock
    private WorkflowInstanceDao workflowInstanceDao;
    @Mock
    private WorkflowTaskRelationDao workflowTaskRelationDao;
    @Mock
    private WorkflowDefinitionService workflowDefinitionService;
    @Mock
    private ExecutorService executorService;

    @InjectMocks
    private DataSyncServiceImpl service;

    @Test
    void rejectsDatasourceFromAnotherProjectBeforeCreatingTask() {
        DataSyncTaskRequest request = new DataSyncTaskRequest();
        request.setName("order_sync");
        request.setParameters(JSONUtils.parseObject("""
                {
                  "source": {
                    "type": "mysql-cdc",
                    "datasourceId": "1",
                    "startup": {"mode": "INITIAL"},
                    "tables": [{"database": "src_db", "table": "orders"}]
                  },
                  "sink": {
                    "type": "jdbc-mysql",
                    "datasourceId": "2",
                    "tables": [{"database": "dst_db", "table": "orders", "write": {"mode": "UPSERT"}}]
                  },
                  "route": [{"source-table": "src_db.orders", "sink-table": "dst_db.orders"}],
                  "transform": [],
                  "pipeline": {"execution.runtime-mode": "STREAMING", "pipeline.table-scope": "DATABASE"}
                }
                """));
        DataSource source = new DataSource();
        source.setType(DbType.MYSQL);
        when(dataSourceDao.queryByIdAndProjectCode(1, PROJECT_CODE)).thenReturn(source);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createTask(new User(), PROJECT_CODE, request));

        assertTrue(error.getMessage().contains("current project"));
        verify(dataSourceDao).queryByIdAndProjectCode(1, PROJECT_CODE);
        verify(dataSourceDao).queryByIdAndProjectCode(2, PROJECT_CODE);
        verifyNoInteractions(workflowDefinitionService);
    }

    @Test
    void taskListNeedsReadPermissionOnly() {
        when(taskDefinitionDao.queryDataSyncPage(any(), eq(PROJECT_CODE), eq("DATA_SYNC"),
                isNull(), isNull(), isNull())).thenReturn(new Page<>(1, 20));
        User user = new User();

        assertEquals(0, service.listTasks(user, PROJECT_CODE, 1, 20, null, null)
                .getPagination().getTotal());

        verify(projectService).checkProjectAndAuthThrowException(user, PROJECT_CODE, null);
        verify(projectService, never()).checkHasProjectWritePermissionThrowException(user, PROJECT_CODE);
    }

    @Test
    void historicalInstanceDoesNotRequireLiveWorkflowRelation() {
        TaskInstance instance = new TaskInstance();
        instance.setId(42);
        instance.setProjectCode(PROJECT_CODE);
        instance.setTaskCode(99L);
        instance.setTaskType("DATA_SYNC");
        instance.setTaskDefinitionVersion(1);
        instance.setState(TaskExecutionStatus.RUNNING_EXECUTION);
        when(taskInstanceDao.queryById(42)).thenReturn(instance);

        assertEquals("42", service.getInstance(new User(), PROJECT_CODE, 42).getId());

        verifyNoInteractions(workflowTaskRelationDao, workflowDefinitionDao);
    }
}
