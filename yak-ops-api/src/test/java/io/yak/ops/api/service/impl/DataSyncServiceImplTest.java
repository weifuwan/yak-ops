package io.yak.ops.api.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.api.dto.datasync.DataSyncPublishRequest;
import io.yak.ops.api.dto.datasync.DataSyncTaskRequest;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.service.ProjectService;
import io.yak.ops.api.service.WorkflowDefinitionService;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.vo.DataSyncOverviewVO;
import io.yak.ops.api.vo.DataSyncTaskVO;
import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.ReleaseState;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.entity.WorkflowTaskRelation;
import io.yak.ops.dao.model.DataSyncOverviewTaskRow;
import io.yak.ops.dao.model.DataSyncTaskPageRow;
import io.yak.ops.dao.repository.DataSourceDao;
import io.yak.ops.dao.repository.TaskDefinitionDao;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.dao.repository.WorkflowDefinitionDao;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.dao.repository.WorkflowTaskRelationDao;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.spi.enums.DbType;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

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
    private TaskInstanceDao taskInstanceDao;
    @Mock
    private WorkflowDefinitionDao workflowDefinitionDao;
    @Mock
    private WorkflowInstanceDao workflowInstanceDao;
    @Mock
    private WorkflowTaskRelationDao workflowTaskRelationDao;
    @Mock
    private WorkflowDefinitionService workflowDefinitionService;

    @InjectMocks
    private DataSyncServiceImpl service;

    @Test
    void overviewCountsCurrentProjectTasksAndLatestWorkflowStates() {
        DataSyncOverviewTaskRow database = overviewTask(
                "{\"definition\":{\"source\":{\"tables\":\"sales.\\\\.*\"},\"pipeline\":{\"execution.runtime-mode\":\"BATCH\"}}}",
                ReleaseState.ONLINE, 1);
        DataSyncOverviewTaskRow multiTable = overviewTask(
                "{\"definition\":{\"source\":{\"tables\":\"sales.orders,sales.users\"},\"pipeline\":{\"execution.runtime-mode\":\"STREAMING\"}}}",
                ReleaseState.ONLINE, 14);
        DataSyncOverviewTaskRow singleTable = overviewTask(
                "{\"definition\":{\"source\":{\"tables\":\"sales.orders\"},\"pipeline\":{\"execution.runtime-mode\":\"STREAMING\"}}}",
                ReleaseState.OFFLINE, 6);
        when(taskDefinitionDao.queryDataSyncOverview(PROJECT_CODE))
                .thenReturn(Arrays.asList(database, multiTable, singleTable));
        when(dataSourceDao.countByProjectCode(PROJECT_CODE)).thenReturn(7L);
        Page<TaskInstance> failurePage = new Page<>(1, 5);
        failurePage.setRecords(Collections.singletonList(
                TaskInstance.builder().id(42).taskCode(99L).name("orders").build()));
        when(taskInstanceDao.queryByProjectAndTaskType(any(), eq(PROJECT_CODE), eq("DATA_SYNC"),
                isNull(), any(int[].class))).thenReturn(failurePage);

        DataSyncOverviewVO overview = service.getOverview(new User(), PROJECT_CODE);

        assertEquals(7L, overview.getDataSourceCount());
        assertEquals(3, overview.getTaskCount());
        assertEquals(2, overview.getOnlineCount());
        assertEquals(1, overview.getOfflineCount());
        assertEquals(1, overview.getRunningCount());
        assertEquals(1, overview.getQueuedCount());
        assertEquals(1, overview.getFailedCount());
        assertEquals(0, overview.getUnreadableCount());
        assertEquals(1, overview.getTaskTypes().get(0).getOffline());
        assertEquals(1, overview.getTaskTypes().get(1).getRealtime());
        assertEquals(1, overview.getTaskTypes().get(2).getRealtime());
        assertEquals("42", overview.getRecentFailures().get(0).getInstanceId());
        verify(projectService).checkProjectAndAuthThrowException(any(), eq(PROJECT_CODE), isNull());
    }

    private static DataSyncOverviewTaskRow overviewTask(String params, ReleaseState release, int state) {
        DataSyncOverviewTaskRow row = new DataSyncOverviewTaskRow();
        row.setTaskParams(params);
        row.setReleaseState(release.getCode());
        row.setLatestWorkflowState(state);
        return row;
    }

    @Test
    void rejectsDatasourceFromAnotherProjectBeforeCreatingTask() {
        DataSyncTaskRequest request = new DataSyncTaskRequest();
        request.setName("order_sync");
        request.setParameters(JSONUtils.parseObject(
                "{\"definition\":{\"source\":{\"type\":\"mysql\",\"datasource-id\":\"1\",\"tables\":\"src_db.orders\",\"scan.startup.mode\":\"initial\"},\"sink\":{\"type\":\"jdbc\",\"datasource-id\":\"2\",\"exclude.schema.changes\":[\"drop.column\"]},\"route\":[{\"source-table\":\"src_db.orders\",\"sink-table\":\"dst_db.orders\"}],\"pipeline\":{\"execution.runtime-mode\":\"STREAMING\"}}}"));
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
    void rejectsUnsupportedDatasourceTypeBeforeCreatingTask() {
        DataSyncTaskRequest request = new DataSyncTaskRequest();
        request.setName("order_sync");
        request.setParameters(JSONUtils.parseObject(
                "{\"definition\":{\"source\":{\"type\":\"mysql\",\"datasource-id\":\"1\",\"tables\":\"src_db.orders\",\"scan.startup.mode\":\"initial\"},\"sink\":{\"type\":\"jdbc\",\"datasource-id\":\"2\",\"exclude.schema.changes\":[\"drop.column\"]},\"route\":[{\"source-table\":\"src_db.orders\",\"sink-table\":\"dst_db.orders\"}],\"pipeline\":{\"execution.runtime-mode\":\"STREAMING\"}}}"));
        DataSource source = new DataSource();
        source.setType(DbType.MYSQL);
        DataSource sink = new DataSource();
        sink.setType(DbType.POSTGRESQL);
        when(dataSourceDao.queryByIdAndProjectCode(1, PROJECT_CODE)).thenReturn(source);
        when(dataSourceDao.queryByIdAndProjectCode(2, PROJECT_CODE)).thenReturn(sink);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createTask(new User(), PROJECT_CODE, request));

        assertEquals(Status.REQUEST_PARAMS_NOT_VALID_ERROR.getCode(), error.getCode());
        verifyNoInteractions(workflowDefinitionService);
    }

    @Test
    void rejectsCredentialsEmbeddedInTaskDefinition() {
        DataSyncTaskRequest request = new DataSyncTaskRequest();
        request.setName("order_sync");
        request.setParameters(JSONUtils.parseObject(
                "{\"definition\":{\"source\":{\"auth\":{\"token\":\"must-not-be-saved\"}}}}"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createTask(new User(), PROJECT_CODE, request));

        assertEquals(Status.REQUEST_PARAMS_NOT_VALID_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains(
                "Pipeline must reference Yak Ops datasources, not inline credentials"));
        verifyNoInteractions(dataSourceDao, workflowDefinitionService);
    }

    @Test
    void rejectsTaskDefinitionWithoutPipelineDefinition() {
        DataSyncTaskRequest request = new DataSyncTaskRequest();
        request.setName("order_sync");
        request.setParameters(JSONUtils.parseObject("{}"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createTask(new User(), PROJECT_CODE, request));

        assertEquals(Status.REQUEST_PARAMS_NOT_VALID_ERROR.getCode(), error.getCode());
        verifyNoInteractions(dataSourceDao, workflowDefinitionService);
    }

    @Test
    void taskStatusFilterUsesNativeReleaseState() {
        when(taskDefinitionDao.queryDataSyncPage(any(), eq(PROJECT_CODE), eq("DATA_SYNC"),
                isNull(), isNull(), eq(ReleaseState.ONLINE.getCode()))).thenReturn(new Page<>(1, 20));

        assertEquals(0, service.listTasks(new User(), PROJECT_CODE, 1, 20, null, ReleaseState.ONLINE)
                .getTotal());
    }

    @Test
    void archiveHidesTaskWithoutDeletingHistoricalInstances() {
        TaskDefinition task = taskDefinition(99L);
        task.setTaskParams("{}");
        WorkflowDefinition workflow = workflow(101L, 99L);
        WorkflowTaskRelation relation = relation(101L, 99L);
        when(taskDefinitionDao.queryByCode(99L)).thenReturn(task);
        when(workflowTaskRelationDao.queryByTaskCode(99L)).thenReturn(Collections.singletonList(relation));
        when(workflowTaskRelationDao.queryByWorkflowDefinitionCode(101L))
                .thenReturn(Collections.singletonList(relation));
        when(workflowDefinitionDao.queryByCode(101L)).thenReturn(Optional.of(workflow));
        when(workflowInstanceDao.queryByWorkflowDefinitionCodeAndStatus(eq(101L), any()))
                .thenReturn(Collections.emptyList());
        when(taskDefinitionDao.updateById(task)).thenReturn(true);
        when(workflowDefinitionDao.updateById(workflow)).thenReturn(true);

        service.deleteTask(new User(), PROJECT_CODE, 99L);

        assertEquals(Flag.NO, task.getFlag());
        assertEquals(Flag.NO, workflow.getFlag());
        assertTrue(service.getTask(new User(), PROJECT_CODE, 99L).isArchived());
        assertThrows(ServiceException.class, () -> service.publish(new User(), PROJECT_CODE, 99L,
                publication(1)));
        verifyNoInteractions(workflowDefinitionService, taskInstanceDao);
    }

    @Test
    void archivedTaskInstancesRemainQueryableByTaskCode() {
        TaskDefinition task = taskDefinition(99L);
        task.setFlag(Flag.NO);
        Page<TaskInstance> page = new Page<>(1, 20);
        page.setTotal(2);
        when(taskDefinitionDao.queryByCode(99L)).thenReturn(task);
        when(taskInstanceDao.queryByProjectAndTaskType(any(), eq(PROJECT_CODE), eq("DATA_SYNC"),
                eq(99L), isNull())).thenReturn(page);

        assertEquals(2, service.listInstances(new User(), PROJECT_CODE, 1, 20, 99L, null).getTotal());
    }

    @Test
    void updateRejectsOutdatedRevision() {
        TaskDefinition task = taskDefinition(99L);
        task.setVersion(4);
        WorkflowDefinition workflow = workflow(101L, 99L);
        WorkflowTaskRelation relation = relation(101L, 99L);
        when(taskDefinitionDao.queryByCode(99L)).thenReturn(task);
        when(workflowTaskRelationDao.queryByTaskCode(99L)).thenReturn(Collections.singletonList(relation));
        when(workflowTaskRelationDao.queryByWorkflowDefinitionCode(101L))
                .thenReturn(Collections.singletonList(relation));
        when(workflowDefinitionDao.queryByCode(101L)).thenReturn(Optional.of(workflow));
        DataSyncTaskRequest request = new DataSyncTaskRequest();
        request.setExpectedRevision(3);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateTask(testUser(), PROJECT_CODE, 99L, request));

        assertEquals(Status.REQUEST_PARAMS_NOT_VALID_ERROR.getCode(), error.getCode());
        verifyNoInteractions(workflowDefinitionService, dataSourceDao);
    }

    @Test
    void updateRejectsRacingRevisionChanges() {
        prepareDataSyncTask(ReleaseState.OFFLINE);
        DataSyncTaskRequest request = new DataSyncTaskRequest();
        request.setName("changed_sync");
        request.setRemark("updated");
        request.setExpectedRevision(4);
        request.setParameters(validParameters());

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateTask(testUser(), PROJECT_CODE, 99L, request));

        assertEquals(Status.REQUEST_PARAMS_NOT_VALID_ERROR.getCode(), error.getCode());
        verify(taskDefinitionDao).compareAndSetDataSyncRevision(PROJECT_CODE, 99L, 4);
        verifyNoInteractions(workflowDefinitionService);
    }

    @Test
    void updateSkipsUnchangedDefinitionsWithoutConsumingRevision() {
        prepareDataSyncTask(ReleaseState.OFFLINE);
        DataSyncTaskRequest request = new DataSyncTaskRequest();
        request.setName("order_sync");
        request.setRemark("original");
        request.setExpectedRevision(4);
        request.setParameters(validParameters());

        service.updateTask(testUser(), PROJECT_CODE, 99L, request);

        verify(taskDefinitionDao, never()).compareAndSetDataSyncRevision(eq(PROJECT_CODE), eq(99L), eq(4));
        verifyNoInteractions(workflowDefinitionService);
    }

    private static DataSyncPublishRequest publication(int version) {
        DataSyncPublishRequest request = new DataSyncPublishRequest();
        request.setVersion(version);
        request.setExpectedRevision(version);
        return request;
    }

    @Test
    void publishRejectsStaleTaskVersion() {
        TaskDefinition task = taskDefinition(99L);
        task.setVersion(4);
        when(taskDefinitionDao.queryByCode(99L)).thenReturn(task);
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.publish(testUser(), PROJECT_CODE, 99L, publication(3)));
        assertEquals(Status.REQUEST_PARAMS_NOT_VALID_ERROR.getCode(), error.getCode());
        verifyNoInteractions(workflowDefinitionService);
    }

    @Test
    void publishRejectsOutdatedExpectedRevision() {
        TaskDefinition task = taskDefinition(99L);
        task.setVersion(4);
        when(taskDefinitionDao.queryByCode(99L)).thenReturn(task);
        DataSyncPublishRequest request = publication(4);
        request.setExpectedRevision(3);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.publish(testUser(), PROJECT_CODE, 99L, request));

        assertEquals(Status.REQUEST_PARAMS_NOT_VALID_ERROR.getCode(), error.getCode());
        verifyNoInteractions(workflowDefinitionService);
    }

    @Test
    void publishCurrentVersionThroughWorkflowService() {
        prepareDataSyncTask(ReleaseState.OFFLINE);
        service.publish(testUser(), PROJECT_CODE, 99L, publication(4));
        verify(workflowDefinitionService).onlineWorkflowDefinition(any(), eq(PROJECT_CODE), eq(101L));
    }

    private static User testUser() {
        User user = new User();
        user.setId(1);
        return user;
    }

    private void prepareDataSyncTask(ReleaseState releaseState) {
        TaskDefinition task = taskDefinition(99L);
        task.setVersion(4);
        task.setName("order_sync");
        task.setDescription("original");
        task.setWorkerGroup("default");
        task.setTaskParams(JSONUtils.toJsonString(validParameters()));
        WorkflowDefinition workflow = workflow(101L, 99L);
        workflow.setReleaseState(releaseState);
        WorkflowTaskRelation relation = relation(101L, 99L);
        when(taskDefinitionDao.queryByCode(99L)).thenReturn(task);
        when(workflowTaskRelationDao.queryByTaskCode(99L)).thenReturn(Collections.singletonList(relation));
        when(workflowTaskRelationDao.queryByWorkflowDefinitionCode(101L))
                .thenReturn(Collections.singletonList(relation));
        when(workflowDefinitionDao.queryByCode(101L)).thenReturn(Optional.of(workflow));

        DataSource mysql = new DataSource();
        mysql.setType(DbType.MYSQL);
        when(dataSourceDao.queryByIdAndProjectCode(1, PROJECT_CODE)).thenReturn(mysql);
        when(dataSourceDao.queryByIdAndProjectCode(2, PROJECT_CODE)).thenReturn(mysql);
    }

    private static com.fasterxml.jackson.databind.JsonNode validParameters() {
        return JSONUtils.parseObject(
                "{\"definition\":{\"source\":{\"type\":\"mysql\",\"datasource-id\":\"1\",\"tables\":\"src_db.orders\",\"scan.startup.mode\":\"initial\"},\"sink\":{\"type\":\"jdbc\",\"datasource-id\":\"2\",\"exclude.schema.changes\":[\"drop.column\"]},\"route\":[{\"source-table\":\"src_db.orders\",\"sink-table\":\"dst_db.orders\"}],\"pipeline\":{\"execution.runtime-mode\":\"STREAMING\"}}}");
    }

    @Test
    void taskReturnsNativeReleaseState() {
        TaskDefinition task = taskDefinition(99L);
        WorkflowDefinition workflow = workflow(101L, 99L);
        workflow.setReleaseState(ReleaseState.ONLINE);
        WorkflowTaskRelation relation = relation(101L, 99L);
        task.setTaskParams("{}");
        when(taskDefinitionDao.queryByCode(99L)).thenReturn(task);
        when(workflowTaskRelationDao.queryByTaskCode(99L)).thenReturn(Collections.singletonList(relation));
        when(workflowTaskRelationDao.queryByWorkflowDefinitionCode(101L))
                .thenReturn(Collections.singletonList(relation));
        when(workflowDefinitionDao.queryByCode(101L)).thenReturn(Optional.of(workflow));

        assertEquals(ReleaseState.ONLINE, service.getTask(new User(), PROJECT_CODE, 99L).getStatus());
    }

    @Test
    void instanceReturnsOriginalExecutionStateAndSchedulerTrigger() {
        TaskInstance instance = taskInstance(42);
        instance.setState(TaskExecutionStatus.FORCED_SUCCESS);
        WorkflowInstance workflow = new WorkflowInstance();
        workflow.setId(34);
        workflow.setCommandType(CommandType.SCHEDULER);
        when(taskInstanceDao.queryById(42)).thenReturn(instance);
        when(workflowInstanceDao.queryById(34)).thenReturn(workflow);

        assertEquals(TaskExecutionStatus.FORCED_SUCCESS,
                service.getInstance(new User(), PROJECT_CODE, 42).getStatus());
        assertEquals("SCHEDULER", service.getInstance(new User(), PROJECT_CODE, 42).getTriggerType());
    }

    @Test
    void instanceStatusFilterUsesTaskExecutionStatusCode() {
        when(taskInstanceDao.queryByProjectAndTaskType(any(), eq(PROJECT_CODE),
                eq("DATA_SYNC"), isNull(), argThat(states -> states != null && states.length == 1
                        && states[0] == TaskExecutionStatus.DISPATCH.getCode())))
                .thenReturn(new Page<>(1, 20));
        assertEquals(0, service.listInstances(new User(), PROJECT_CODE, 1, 20,
                null, TaskExecutionStatus.DISPATCH).getTotal());
    }

    private static TaskDefinition taskDefinition(long taskCode) {
        TaskDefinition task = new TaskDefinition();
        task.setCode(taskCode);
        task.setProjectCode(PROJECT_CODE);
        task.setTaskType("DATA_SYNC");
        task.setFlag(Flag.YES);
        return task;
    }

    private static WorkflowDefinition workflow(long code, long taskCode) {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setCode(code);
        workflow.setProjectCode(PROJECT_CODE);
        workflow.setName("data_sync_" + taskCode);
        workflow.setReleaseState(ReleaseState.OFFLINE);
        workflow.setFlag(Flag.YES);
        return workflow;
    }

    private static WorkflowTaskRelation relation(long workflowCode, long taskCode) {
        WorkflowTaskRelation relation = new WorkflowTaskRelation();
        relation.setWorkflowDefinitionCode(workflowCode);
        relation.setProjectCode(PROJECT_CODE);
        relation.setPreTaskCode(0);
        relation.setPostTaskCode(taskCode);
        return relation;
    }

    private static TaskInstance taskInstance(int id) {
        TaskInstance instance = new TaskInstance();
        instance.setId(id);
        instance.setProjectCode(PROJECT_CODE);
        instance.setTaskCode(99L);
        instance.setTaskType("DATA_SYNC");
        instance.setWorkflowInstanceId(34);
        return instance;
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
        Page<DataSyncTaskPageRow> page = new Page<>(2, 20);
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
    void taskPageUsesWorkflowMetadataFromSingleQuery() {
        DataSyncTaskPageRow row = new DataSyncTaskPageRow();
        row.setCode(99L);
        row.setName("orders");
        row.setVersion(4);
        row.setFlag(Flag.YES);
        row.setTaskParams(JSONUtils.toJsonString(validParameters()));
        row.setWorkflowCode(101L);
        row.setWorkflowReleaseState(ReleaseState.ONLINE.getCode());
        Page<DataSyncTaskPageRow> page = new Page<>(1, 20);
        page.setRecords(Collections.singletonList(row));
        page.setTotal(1);
        when(taskDefinitionDao.queryDataSyncPage(any(), eq(PROJECT_CODE), eq("DATA_SYNC"),
                isNull(), isNull(), isNull())).thenReturn(page);

        DataSyncTaskVO task = service.listTasks(new User(), PROJECT_CODE, 1, 20, null, null)
                .getTotalList().get(0);

        assertEquals("101", task.getWorkflowCode());
        assertEquals(ReleaseState.ONLINE, task.getStatus());
        verifyNoInteractions(workflowDefinitionDao, workflowTaskRelationDao);
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
