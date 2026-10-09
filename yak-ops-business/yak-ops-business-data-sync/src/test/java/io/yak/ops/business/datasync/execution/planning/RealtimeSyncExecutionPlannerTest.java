package io.yak.ops.business.datasync.execution.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.execution.planning.target.TargetTablePreflight;
import io.yak.ops.business.datasync.execution.realtime.RealtimeSyncStateNamespace;
import io.yak.ops.business.datasync.schema.catalog.SourceTableIntrospector;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncEndpointSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRealtimeConfigVO;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Types;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RealtimeSyncExecutionPlannerTest {

    @Test
    void shouldBuildMySqlCdcToJdbcChangelogPlan() throws Exception {
        RealtimeSyncExecutionPlanner planner = new RealtimeSyncExecutionPlanner();
        DataSourceService dataSourceService = dataSourceService();
        injectDataSourceService(planner, dataSourceService);
        injectTargetTablePreflight(planner, targetTablePreflight(dataSourceService));
        injectStateNamespace(planner, new RealtimeSyncStateNamespace());

        RealtimeSyncExecutionPlan plan =
                planner.plan("workspace-1", snapshot(DataSyncType.REALTIME.name()), 54021L);

        assertNotNull(plan.source());
        assertNotNull(plan.sink());
        assertEquals(List.of("id"), plan.sourceSchema().primaryKeys());
        assertEquals(
                List.of("id", "name"),
                plan.sourceSchema().columns().stream().map(column -> column.name()).toList());
        assertEquals(Duration.ofSeconds(7), plan.checkpointInterval());
    }

    @Test
    void shouldRejectNonRealtimeSnapshot() throws Exception {
        RealtimeSyncExecutionPlanner planner = new RealtimeSyncExecutionPlanner();
        DataSourceService dataSourceService = dataSourceService();
        injectDataSourceService(planner, dataSourceService);
        injectTargetTablePreflight(planner, targetTablePreflight(dataSourceService));
        injectStateNamespace(planner, new RealtimeSyncStateNamespace());

        assertThrows(
                IllegalArgumentException.class,
                () -> planner.plan("workspace-1", snapshot(DataSyncType.OFFLINE.name()), 54021L));
    }

    private void injectDataSourceService(RealtimeSyncExecutionPlanner planner, DataSourceService service)
            throws Exception {
        Field field = RealtimeSyncExecutionPlanner.class.getDeclaredField("dataSourceService");
        field.setAccessible(true);
        field.set(planner, service);
    }

    private void injectTargetTablePreflight(
            RealtimeSyncExecutionPlanner planner, TargetTablePreflight preparer) throws Exception {
        Field field = RealtimeSyncExecutionPlanner.class.getDeclaredField("targetTablePreflight");
        field.setAccessible(true);
        field.set(planner, preparer);
    }

    private void injectStateNamespace(RealtimeSyncExecutionPlanner planner, RealtimeSyncStateNamespace stateNamespace)
            throws Exception {
        Field field = RealtimeSyncExecutionPlanner.class.getDeclaredField("stateNamespace");
        field.setAccessible(true);
        field.set(planner, stateNamespace);
    }

    private TargetTablePreflight targetTablePreflight(DataSourceService dataSourceService) throws Exception {
        TargetTablePreflight preflight = new TargetTablePreflight();
        Field dataSourceField = TargetTablePreflight.class.getDeclaredField("dataSourceService");
        dataSourceField.setAccessible(true);
        dataSourceField.set(preflight, dataSourceService);

        SourceTableIntrospector sourceTableIntrospector = new SourceTableIntrospector();
        Field introspectorField = SourceTableIntrospector.class.getDeclaredField("dataSourceService");
        introspectorField.setAccessible(true);
        introspectorField.set(sourceTableIntrospector, dataSourceService);

        Field sourceTableField = TargetTablePreflight.class.getDeclaredField("sourceTableIntrospector");
        sourceTableField.setAccessible(true);
        sourceTableField.set(preflight, sourceTableIntrospector);
        return preflight;
    }

    private DataSourceService dataSourceService() {
        DataSourceConnection sourceConnection = connection("MYSQL", "source_db");
        DataSourceConnection targetConnection = connection("POSTGRE_SQL", "target_db");
        DataSourceCatalogTableVO sourceTable = catalogTable("source_db", "source_user");
        DataSourceCatalogTableVO targetTable = catalogTable("target_db", "target_user");
        List<DataSourceCatalogColumnVO> sourceColumns =
                List.of(column("id", Types.BIGINT, 1, true), column("name", Types.VARCHAR, 2, false));
        List<DataSourceCatalogColumnVO> targetColumns =
                List.of(column("ID", Types.BIGINT, 1, true), column("NAME", Types.VARCHAR, 2, false));

        return (DataSourceService) Proxy.newProxyInstance(
                DataSourceService.class.getClassLoader(),
                new Class<?>[] {DataSourceService.class},
                (proxy, method, args) -> {
                    if ("findCatalogTable".equals(method.getName())) {
                        return Optional.of("source".equals(args[0]) ? sourceTable : targetTable);
                    }
                    if ("queryCatalogTable".equals(method.getName())) {
                        return "source".equals(args[0]) ? sourceTable : targetTable;
                    }
                    if ("queryCatalogColumns".equals(method.getName())) {
                        return "source".equals(args[0]) ? sourceColumns : targetColumns;
                    }
                    if ("resolveRuntimeConnection".equals(method.getName())) {
                        return "source".equals(args[0]) ? sourceConnection : targetConnection;
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

    private JdbcConnectionProperties connection(String type, String database) {
        return new JdbcConnectionProperties(
                type,
                "jdbc:test://" + database,
                "test.Driver",
                "user",
                "password",
                database,
                null,
                Map.of(),
                "{}");
    }

    private DataSyncDefinitionSnapshotVO snapshot(String syncType) {
        DataSyncDefinitionSnapshotVO snapshot = new DataSyncDefinitionSnapshotVO();
        snapshot.setTaskId("task-1");
        snapshot.setTaskName("realtime-sync");
        snapshot.setTaskVersion(1);
        snapshot.setSyncType(syncType);
        snapshot.setSource(endpoint("source", "MYSQL", "source_db", "source_user"));
        snapshot.setTarget(endpoint("target", "POSTGRE_SQL", "target_db", "target_user"));

        DataSyncRealtimeConfigVO config = new DataSyncRealtimeConfigVO();
        config.setCheckpointIntervalSeconds(7);
        config.setQueueCapacity(64);
        config.setPollBatchSize(500);
        config.setWriteBatchSize(200);
        config.setTimeoutSeconds(30);
        snapshot.setRealtimeConfig(config);
        return snapshot;
    }

    private DataSyncEndpointSnapshotVO endpoint(
            String dataSourceId, String dataSourceType, String database, String table) {
        DataSyncEndpointSnapshotVO endpoint = new DataSyncEndpointSnapshotVO();
        endpoint.setDataSourceId(dataSourceId);
        endpoint.setDataSourceName(dataSourceId);
        endpoint.setDataSourceType(dataSourceType);
        endpoint.setDatabase(database);
        endpoint.setTable(table);
        return endpoint;
    }

    private DataSourceCatalogColumnVO column(String name, int jdbcType, int ordinal, boolean primaryKey) {
        DataSourceCatalogColumnVO column = new DataSourceCatalogColumnVO();
        column.setName(name);
        column.setTypeName(jdbcType == Types.BIGINT ? "BIGINT" : "VARCHAR");
        column.setJdbcType(jdbcType);
        column.setSize(jdbcType == Types.BIGINT ? 19 : 100);
        column.setScale(jdbcType == Types.BIGINT ? 0 : null);
        column.setNullable(!primaryKey);
        column.setOrdinalPosition(ordinal);
        column.setPrimaryKey(primaryKey);
        return column;
    }
}
