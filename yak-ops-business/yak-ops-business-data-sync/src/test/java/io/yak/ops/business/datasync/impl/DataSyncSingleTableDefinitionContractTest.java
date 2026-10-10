package io.yak.ops.business.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.bean.dto.datasync.DataSyncTableRouteDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.dao.entity.datasync.DataSyncTableRouteEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.datasync.DataSyncTableRouteRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 单表任务不再双写 Route，历史多表定义只能显式拒绝。 */
class DataSyncSingleTableDefinitionContractTest {

    @Test
    void shouldRejectExplicitRouteRequests() {
        DataSyncTaskDefinitionValidator validator = new DataSyncTaskDefinitionValidator();
        DataSyncTaskDTO dto = new DataSyncTaskDTO();
        dto.setTableRoutes(List.of(new DataSyncTableRouteDTO()));

        DataSyncException error = assertThrows(
                DataSyncException.class, () -> validator.rejectMultiRouteRequest(dto));
        assertEquals(DataSyncErrorCode.INVALID_TASK, error.getErrorCode());
    }

    @Test
    void shouldRejectPersistedHistoricalMultiRouteTask() throws Exception {
        DataSyncTaskDefinitionValidator validator = new DataSyncTaskDefinitionValidator();
        DataSyncTableRouteRepository repository = DataSyncTestTableRouteRepository.create();
        repository.add(route("route-1", 0));
        repository.add(route("route-2", 1));
        DataSyncTestServices.inject(validator, "tableRouteRepository", repository);

        DataSyncTaskEntity task = new DataSyncTaskEntity();
        task.setId("task-1");
        task.setWorkspaceId("workspace-1");
        DataSyncException error =
                assertThrows(DataSyncException.class, () -> validator.requireSingleTableTask(task));
        assertEquals(DataSyncErrorCode.INVALID_TASK, error.getErrorCode());
        assertEquals(2, repository.queryByTask("workspace-1", "task-1").size());
    }

    private DataSyncTableRouteEntity route(String id, int order) {
        DataSyncTableRouteEntity value = new DataSyncTableRouteEntity();
        value.setId(id);
        value.setWorkspaceId("workspace-1");
        value.setTaskId("task-1");
        value.setSortOrder(order);
        return value;
    }
}
