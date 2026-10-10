package io.yak.ops.business.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTaskRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataSyncInstanceSyncTypeContractTest {

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void shouldNotCreateRealtimeInstanceWithoutExecutionEngine() throws Exception {
        DataSyncInstanceServiceImpl service = new DataSyncInstanceServiceImpl();
        DataSyncTaskEntity task = task();
        DataSyncTestTableRouteRepository.inject(service, task);
        DataSyncTestTableExecutionRepository.inject(service);
        AtomicReference<DataSyncInstanceEntity> captured = new AtomicReference<>();
        inject(service, "taskRepository", taskRepository(task));
        inject(service, "instanceRepository", instanceRepository(captured));
        injectDataSourceService(service, dataSourceService(List.of(primaryKeyColumn("id")), List.of(primaryKeyColumn("ID"))));

        WorkspaceContext.bind("workspace-1");
        DataSyncException exception =
                assertThrows(DataSyncException.class, () -> service.runTask("task-1"));

        assertEquals(DataSyncErrorCode.EXECUTION_FAILED, exception.getErrorCode());
        assertEquals(null, captured.get());
    }

    @Test
    void shouldRejectRealtimeRunWhenTargetPrimaryKeyDoesNotMatchSource() throws Exception {
        DataSyncInstanceServiceImpl service = new DataSyncInstanceServiceImpl();
        DataSyncTaskEntity task = task();
        DataSyncTestTableRouteRepository.inject(service, task);
        DataSyncTestTableExecutionRepository.inject(service);
        AtomicReference<DataSyncInstanceEntity> captured = new AtomicReference<>();
        inject(service, "taskRepository", taskRepository(task));
        inject(service, "instanceRepository", instanceRepository(captured));
        injectDataSourceService(service, dataSourceService(List.of(primaryKeyColumn("id")), List.of(nonPrimaryKeyColumn("id"))));

        WorkspaceContext.bind("workspace-1");
        DataSyncException exception = assertThrows(DataSyncException.class, () -> service.runTask("task-1"));

        assertEquals(DataSyncErrorCode.INVALID_TASK, exception.getErrorCode());
        assertEquals("同步任务参数不合法：更新写入要求来源与目标表主键同名且完整", exception.getUserMessage());
        assertEquals(null, captured.get());
    }

    private DataSyncTaskRepository taskRepository(DataSyncTaskEntity task) {
        return (DataSyncTaskRepository) Proxy.newProxyInstance(
                DataSyncTaskRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncTaskRepository.class},
                (proxy, method, args) -> {
                    if ("queryById".equals(method.getName()) && args != null && args.length == 2) {
                        return Optional.of(task);
                    }
                    if ("update".equals(method.getName())) return args[1];
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncInstanceRepository instanceRepository(
            AtomicReference<DataSyncInstanceEntity> captured) {
        return (DataSyncInstanceRepository) Proxy.newProxyInstance(
                DataSyncInstanceRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncInstanceRepository.class},
                (proxy, method, args) -> {
                    if ("existsActiveByTask".equals(method.getName())) return false;
                    if ("add".equals(method.getName())) {
                        DataSyncInstanceEntity entity = (DataSyncInstanceEntity) args[0];
                        captured.set(entity);
                        return entity;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSourceService dataSourceService(
            List<DataSourceCatalogColumnVO> sourceColumns, List<DataSourceCatalogColumnVO> targetColumns) {
        DataSourceVO source = dataSource("source", "source_db", "MYSQL");
        DataSourceVO target = dataSource("target", "target_db", "MYSQL");
        DataSourceCatalogTableVO sourceTable = catalogTable("source_db", "source_table");
        DataSourceCatalogTableVO targetTable = catalogTable("target_db", "target_table");

        return (DataSourceService) Proxy.newProxyInstance(
                DataSourceService.class.getClassLoader(),
                new Class<?>[] {DataSourceService.class},
                (proxy, method, args) -> {
                    if ("queryDataSource".equals(method.getName())) {
                        return "source".equals(args[0]) ? source : target;
                    }
                    if ("findCatalogTable".equals(method.getName())) {
                        return Optional.of("source".equals(args[0]) ? sourceTable : targetTable);
                    }
                    if ("queryCatalogTable".equals(method.getName())) {
                        return "source".equals(args[0]) ? sourceTable : targetTable;
                    }
                    if ("queryTableSchema".equals(method.getName())) {
                        return DataSyncTestTableSchema.fromColumns("source".equals(args[0]) ? sourceColumns : targetColumns);
                    }
                    if ("queryCatalogColumns".equals(method.getName())) {
                        return "source".equals(args[0]) ? sourceColumns : targetColumns;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSourceCatalogTableVO catalogTable(String database, String table) {
        DataSourceCatalogTableVO value = new DataSourceCatalogTableVO();
        value.setDatabase(database);
        value.setName(table);
        value.setType("TABLE");
        return value;
    }

    private DataSyncTaskEntity task() {
        DataSyncTaskEntity task = new DataSyncTaskEntity();
        task.setId("task-1");
        task.setWorkspaceId("workspace-1");
        task.setName("realtime-task");
        task.setSyncType(DataSyncType.REALTIME);
        task.setStatus(DataSyncTaskStatus.PUBLISHED);
        task.setSourceDataSourceId("source");
        task.setSourceDatabase("source_db");
        task.setSourceTable("source_table");
        task.setTargetDataSourceId("target");
        task.setTargetDatabase("target_db");
        task.setTargetTable("target_table");
        task.setRuntimeConfig(
                "{\"checkpointIntervalSeconds\":10,\"queueCapacity\":64,\"pollBatchSize\":500,"
                        + "\"writeBatchSize\":500,\"timeoutSeconds\":30}");
        task.setRetryPolicy("{\"maxAttempts\":3,\"backoffSeconds\":5}");
        task.setDefinitionVersion(1);
        return task;
    }

    private DataSourceVO dataSource(String id, String database, String type) {
        DataSourceVO value = new DataSourceVO();
        value.setId(id);
        value.setName(id);
        value.setDbType(type);
        value.setDatabase(database);
        return value;
    }

    private DataSourceCatalogColumnVO primaryKeyColumn(String name) {
        DataSourceCatalogColumnVO column = new DataSourceCatalogColumnVO();
        column.setName(name);
        column.setTypeName("BIGINT");
        column.setJdbcType(Types.BIGINT);
        column.setSize(19);
        column.setScale(0);
        column.setNullable(false);
        column.setOrdinalPosition(1);
        column.setPrimaryKey(true);
        return column;
    }

    private DataSourceCatalogColumnVO nonPrimaryKeyColumn(String name) {
        DataSourceCatalogColumnVO column = primaryKeyColumn(name);
        column.setPrimaryKey(false);
        return column;
    }

    private void injectDataSourceService(DataSyncInstanceServiceImpl service, DataSourceService dataSourceService)
            throws Exception {
        inject(service, "dataSourceService", dataSourceService);
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        DataSyncTestServices.inject(target, fieldName, value);
    }

}
