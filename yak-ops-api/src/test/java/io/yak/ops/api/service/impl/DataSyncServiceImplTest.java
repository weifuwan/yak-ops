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
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.service.ExecutorService;
import io.yak.ops.api.service.ProjectService;
import io.yak.ops.api.service.WorkflowDefinitionService;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.vo.DataSyncTaskVO;
import io.yak.ops.api.vo.DataSyncTaskVersionVO;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskDefinitionLog;
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

import java.util.Collections;

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
        request.setParameters(JSONUtils.parseObject(
                "{\"source\":{"
                        + "\"type\":\"mysql-cdc\",\"datasourceId\":\"1\","
                        + "\"startup\":{\"mode\":\"INITIAL\"},"
                        + "\"tables\":[{\"database\":\"src_db\",\"table\":\"orders\"}]},"
                        + "\"sink\":{\"type\":\"jdbc-mysql\",\"datasourceId\":\"2\","
                        + "\"tables\":[{\"database\":\"dst_db\",\"table\":\"orders\","
                        + "\"write\":{\"mode\":\"UPSERT\"}}]},"
                        + "\"route\":[{\"source-table\":\"src_db.orders\",\"sink-table\":\"dst_db.orders\"}],"
                        + "\"transform\":[],"
                        + "\"pipeline\":{\"execution.runtime-mode\":\"STREAMING\",\"pipeline.table-scope\":\"DATABASE\"}}"));
        DataSource source = new DataSource();
        source.setType(DbType.MYSQL);
        when(dataSourceDao.queryByIdAndProjectCode(1, PROJECT_CODE)).thenReturn(source);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createTask(new User(), PROJECT_CODE, request));

        assertEquals(Status.RESOURCE_NOT_EXIST_OR_NO_PERMISSION.getCode(), error.getCode());
        verify(dataSourceDao).queryByIdAndProjectCode(1, PROJECT_CODE);
        verify(dataSourceDao).queryByIdAndProjectCode(2, PROJECT_CODE);
        verifyNoInteractions(workflowDefinitionService);
    }

    @Test
    void rejectsCredentialsEmbeddedInTaskDefinition() {
        DataSyncTaskRequest request = new DataSyncTaskRequest();
        request.setName("order_sync");
        request.setParameters(JSONUtils.parseObject(
                "{\"source\":{\"auth\":{\"token\":\"must-not-be-saved\"}}}"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createTask(new User(), PROJECT_CODE, request));

        assertEquals(Status.REQUEST_PARAMS_NOT_VALID_ERROR.getCode(), error.getCode());
        verifyNoInteractions(dataSourceDao, workflowDefinitionService);
    }

    @Test
    void rejectsUnsupportedStatusWithBusinessError() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.listTasks(new User(), PROJECT_CODE, 1, 20, null, "UNKNOWN"));
        assertEquals(Status.REQUEST_PARAMS_NOT_VALID_ERROR.getCode(), error.getCode());
        verifyNoInteractions(taskDefinitionDao);
    }

    @Test
    void taskListNeedsReadPermissionOnly() {
        when(taskDefinitionDao.queryDataSyncPage(any(), eq(PROJECT_CODE), eq("DATA_SYNC"),
                isNull(), isNull(), isNull())).thenReturn(new Page<>(1, 20));
        User user = new User();

        assertEquals(0, service.listTasks(user, PROJECT_CODE, 1, 20, null, null)
                .getTotal());

        verify(projectService).checkProjectAndAuthThrowException(user, PROJECT_CODE, null);
        verify(projectService, never()).checkHasProjectWritePermissionThrowException(user, PROJECT_CODE);
    }

    @Test
    void taskPagePreservesBackendPaginationFields() {
        Page<TaskDefinition> page = new Page<>(2, 20);
        page.setTotal(45);
        when(taskDefinitionDao.queryDataSyncPage(any(), eq(PROJECT_CODE), eq("DATA_SYNC"),
                isNull(), isNull(), isNull())).thenReturn(page);

        PageInfo<DataSyncTaskVO> result = service.listTasks(new User(), PROJECT_CODE, 2, 20, null, null);

        assertEquals(45, result.getTotal());
        assertEquals(2, result.getCurrentPage());
        assertEquals(20, result.getPageNo());
        assertEquals(3, result.getTotalPage());
        assertTrue(result.getTotalList().isEmpty());
        verifyNoInteractions(workflowTaskRelationDao);
    }

    @Test
    void taskVersionsUseDatabasePagination() {
        TaskDefinition definition = new TaskDefinition();
        definition.setCode(99L);
        definition.setProjectCode(PROJECT_CODE);
        definition.setTaskType("DATA_SYNC");
        when(taskDefinitionDao.queryByCode(99L)).thenReturn(definition);

        Page<TaskDefinitionLog> page = new Page<>(2, 10);
        page.setTotal(21);
        page.setRecords(Collections.emptyList());
        when(taskDefinitionLogDao.queryVersionsPaging(any(), eq(99L), eq(PROJECT_CODE))).thenReturn(page);

        PageInfo<DataSyncTaskVersionVO> result = service.listVersions(new User(), PROJECT_CODE, 99L, 2, 10);

        assertEquals(21, result.getTotal());
        assertEquals(2, result.getCurrentPage());
        assertEquals(3, result.getTotalPage());
        assertTrue(result.getTotalList().isEmpty());
        verify(taskDefinitionLogDao).queryVersionsPaging(any(), eq(99L), eq(PROJECT_CODE));
    }

    @Test
    void versionPageReturnsSummaryWithoutParameters() {
        TaskDefinition definition = new TaskDefinition();
        definition.setCode(99L);
        definition.setProjectCode(PROJECT_CODE);
        definition.setTaskType("DATA_SYNC");
        when(taskDefinitionDao.queryByCode(99L)).thenReturn(definition);
        TaskDefinitionLog version = new TaskDefinitionLog();
        version.setCode(99L);
        version.setVersion(3);
        version.setTaskParams("{\"source\":{\"datasourceId\":\"1\"}}");
        Page<TaskDefinitionLog> page = new Page<>(1, 20);
        page.setRecords(Collections.singletonList(version));
        page.setTotal(1);
        when(taskDefinitionLogDao.queryVersionsPaging(any(), eq(99L), eq(PROJECT_CODE))).thenReturn(page);

        DataSyncTaskVersionVO summary =
                service.listVersions(new User(), PROJECT_CODE, 99L, 1, 20).getTotalList().get(0);

        assertEquals(3, summary.getVersion());
        assertEquals(null, summary.getParameters());
    }

    @Test
    void historicalInstanceDoesNotRequireLiveWorkflowRelation() {
        TaskInstance instance = new TaskInstance();
        instance.setId(42);
        instance.setProjectCode(PROJECT_CODE);
        instance.setTaskCode(99L);
        instance.setTaskType("DATA_SYNC");
        instance.setTaskDefinitionVersion(1);
        instance.setName("order_sync");
        instance.setState(TaskExecutionStatus.RUNNING_EXECUTION);
        when(taskInstanceDao.queryById(42)).thenReturn(instance);

        assertEquals("42", service.getInstance(new User(), PROJECT_CODE, 42).getId());
        assertEquals("order_sync", service.getInstance(new User(), PROJECT_CODE, 42).getTaskName());

        verifyNoInteractions(workflowTaskRelationDao, workflowDefinitionDao);
    }
}
