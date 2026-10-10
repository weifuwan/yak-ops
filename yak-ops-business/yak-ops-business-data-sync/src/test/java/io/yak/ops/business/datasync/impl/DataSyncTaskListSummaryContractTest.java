package io.yak.ops.business.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncDesiredState;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.dao.entity.datasync.DataSyncScheduleEntity;
import io.yak.ops.dao.entity.datasync.SyncDefinitionEntity;
import io.yak.ops.dao.repository.datasync.DataSyncScheduleRepository;
import io.yak.ops.dao.repository.datasync.SyncDefinitionRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataSyncTaskListSummaryContractTest {

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void shouldReturnUpdaterAndScheduleDefinitionWithOneBatchScheduleQuery() throws Exception {
        SyncDefinitionServiceImpl service = new SyncDefinitionServiceImpl();
        DataSyncTestTableRouteRepository.inject(service);
        DataSyncTestTableExecutionRepository.inject(service);
        SyncDefinitionEntity task = task();
        DataSyncScheduleEntity schedule = schedule();
        AtomicReference<List<String>> queriedTaskIds = new AtomicReference<>();

        inject(service, "taskRepository", taskRepository(task));
        inject(service, "scheduleRepository", scheduleRepository(schedule, queriedTaskIds));

        DataSyncTaskQueryDTO query = new DataSyncTaskQueryDTO();
        query.setPageNo(1);
        query.setPageSize(20);
        query.setSyncType(DataSyncType.OFFLINE);

        WorkspaceContext.bind("workspace-1");
        PagingData<DataSyncTaskVO> result = service.queryTaskPage(query);

        assertEquals(List.of("task-1"), queriedTaskIds.get());
        assertEquals(1, result.getBizData().size());
        DataSyncTaskVO row = result.getBizData().getFirst();
        assertEquals("user-1", row.getUpdateBy());
        assertEquals("0 0 2 * * ?", row.getScheduleCronExpression());
        assertEquals("Asia/Shanghai", row.getScheduleTimeZone());
        assertEquals(true, row.getScheduleEnabled());
    }

    private SyncDefinitionEntity task() {
        SyncDefinitionEntity task = new SyncDefinitionEntity();
        task.setId("task-1");
        task.setWorkspaceId("workspace-1");
        task.setName("offline-task");
        task.setSyncType(DataSyncType.OFFLINE);
        task.setStatus(DataSyncTaskStatus.PUBLISHED);
        task.setDesiredState(DataSyncDesiredState.STOPPED);
        task.setWriteMode(DataSyncWriteMode.APPEND);
        task.setRuntimeConfig("{}");
        task.setDefinitionVersion(2);
        task.setUpdateBy("user-1");
        return task;
    }

    private DataSyncScheduleEntity schedule() {
        DataSyncScheduleEntity schedule = new DataSyncScheduleEntity();
        schedule.setId("schedule-1");
        schedule.setWorkspaceId("workspace-1");
        schedule.setTaskId("task-1");
        schedule.setCronExpression("0 0 2 * * ?");
        schedule.setTimeZone("Asia/Shanghai");
        schedule.setEnabled(true);
        return schedule;
    }

    private SyncDefinitionRepository taskRepository(SyncDefinitionEntity task) {
        return (SyncDefinitionRepository) Proxy.newProxyInstance(
                SyncDefinitionRepository.class.getClassLoader(),
                new Class<?>[] {SyncDefinitionRepository.class},
                (proxy, method, args) -> {
                    if ("queryPage".equals(method.getName())) return PageData.of(List.of(task), 1, 1, 20);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private DataSyncScheduleRepository scheduleRepository(
            DataSyncScheduleEntity schedule, AtomicReference<List<String>> queriedTaskIds) {
        return (DataSyncScheduleRepository) Proxy.newProxyInstance(
                DataSyncScheduleRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncScheduleRepository.class},
                (proxy, method, args) -> {
                    if ("queryByTasks".equals(method.getName())) {
                        queriedTaskIds.set((List<String>) args[1]);
                        return List.of(schedule);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        DataSyncTestServices.inject(target, fieldName, value);
    }
}
