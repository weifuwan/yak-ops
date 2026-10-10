package io.yak.ops.business.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.dao.repository.datasync.DataSyncTaskRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataSyncUpsertContractTest {

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void shouldRejectUpsertWhenTargetHasNoPrimaryKey() throws Exception {
        DataSyncServiceImpl service = service(
                List.of(column("id", true), column("name", false)),
                List.of(column("id", false), column("name", false)));

        WorkspaceContext.bind("workspace-1");
        assertThrows(DataSyncException.class, () -> service.createTask(task()));
    }

    @Test
    void shouldRejectUpsertWhenSourceMissesCompositeTargetPrimaryKey() throws Exception {
        DataSyncServiceImpl service = service(
                List.of(column("id", true), column("name", false)),
                List.of(column("id", true), column("tenant_id", true), column("name", false)));

        WorkspaceContext.bind("workspace-1");
        assertThrows(DataSyncException.class, () -> service.createTask(task()));
    }

    private DataSyncServiceImpl service(
            List<DataSourceCatalogColumnVO> sourceColumns, List<DataSourceCatalogColumnVO> targetColumns)
            throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTestTableRouteRepository.inject(service);
        DataSyncTestTableExecutionRepository.inject(service);
        inject(service, "taskRepository", taskRepository());
        injectDataSourceService(service, dataSourceService(sourceColumns, targetColumns));
        return service;
    }

    private DataSyncTaskRepository taskRepository() {
        return (DataSyncTaskRepository) Proxy.newProxyInstance(
                DataSyncTaskRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncTaskRepository.class},
                (proxy, method, args) -> {
                    if ("existsByName".equals(method.getName())) return false;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSourceService dataSourceService(
            List<DataSourceCatalogColumnVO> sourceColumns, List<DataSourceCatalogColumnVO> targetColumns) {
        DataSourceVO source = dataSource("source", "source_db");
        DataSourceVO target = dataSource("target", "target_db");
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

    private DataSyncTaskDTO task() {
        DataSyncTaskDTO task = new DataSyncTaskDTO();
        task.setName("upsert-task");
        task.setSyncType(DataSyncType.OFFLINE);
        task.setWriteMode(DataSyncWriteMode.UPSERT);
        task.setSourceDataSourceId("source");
        task.setSourceTable("source_table");
        task.setTargetDataSourceId("target");
        task.setTargetTable("target_table");
        return task;
    }

    private DataSourceVO dataSource(String id, String database) {
        DataSourceVO value = new DataSourceVO();
        value.setId(id);
        value.setName(id);
        value.setDbType("MYSQL");
        value.setDatabase(database);
        return value;
    }

    private DataSourceCatalogColumnVO column(String name, boolean primaryKey) {
        DataSourceCatalogColumnVO column = new DataSourceCatalogColumnVO();
        column.setName(name);
        column.setTypeName("BIGINT");
        column.setJdbcType(Types.BIGINT);
        column.setSize(19);
        column.setScale(0);
        column.setNullable(false);
        column.setOrdinalPosition("id".equals(name) ? 1 : 2);
        column.setPrimaryKey(primaryKey);
        return column;
    }

    private void injectDataSourceService(DataSyncServiceImpl service, DataSourceService dataSourceService)
            throws Exception {
        inject(service, "dataSourceService", dataSourceService);
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = DataSyncServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
