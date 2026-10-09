package io.yak.ops.business.datasync.execution.planning.target;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.business.datasync.schema.LogicalTable;
import io.yak.ops.business.datasync.schema.catalog.SourceTableIntrospector;
import io.yak.ops.business.datasync.schema.target.TargetTablePlanner;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncColumnMappingVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncEndpointSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncMappingVO;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TargetTablePreflightTest {

    @Test
    void shouldUseExistingCompatibleTargetWithoutCreatingTable() throws Exception {
        AtomicInteger creates = new AtomicInteger();
        TargetTablePreflight preparer = preparer(dataSourceService(new AtomicBoolean(true)), creates);

        TargetTablePreflightResult result = preparer.prepare(snapshot(false), 30);

        assertFalse(result.targetCreated());
        assertEquals(List.of("id"), result.targetWriteSchema().primaryKeys());
        assertEquals(0, creates.get());
    }

    @Test
    void shouldRejectMissingTargetWhenAutoCreateDisabled() throws Exception {
        TargetTablePreflight preparer =
                preparer(dataSourceService(new AtomicBoolean(false)), new AtomicInteger());

        DataSyncException exception =
                assertThrows(DataSyncException.class, () -> preparer.prepare(snapshot(false), 30));

        assertEquals(DataSyncErrorCode.TARGET_TABLE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void shouldCreateMissingTargetAndReIntrospectBeforeUse() throws Exception {
        AtomicBoolean targetExists = new AtomicBoolean(false);
        AtomicInteger creates = new AtomicInteger();
        TargetTablePreflight preparer = preparer(dataSourceService(targetExists), creates);
        inject(preparer, "ddlExecutor", (TargetTableDdlExecutor) (connection, table, schema, timeoutSeconds) -> {
            creates.incrementAndGet();
            targetExists.set(true);
            return "CREATE TABLE";
        });

        TargetTablePreflightResult result = preparer.prepare(snapshot(true), 30);

        assertTrue(result.targetCreated());
        assertEquals(1, creates.get());
        assertEquals(2, result.targetWriteSchema().columnCount());
        assertEquals(List.of("id"), result.targetWriteSchema().primaryKeys());
    }

    @Test
    void shouldProjectRenameAndReorderIntoRuntimeSchemas() throws Exception {
        TargetTablePreflight preparer =
                preparer(mappedDataSourceService(), new AtomicInteger());

        DataSyncDefinitionSnapshotVO snapshot = snapshot(false);
        snapshot.setMapping(mapping(
                columnMapping("name", "display_name"),
                columnMapping("id", "user_id")));

        TargetTablePreflightResult result = preparer.prepare(snapshot, 30);

        assertEquals(
                List.of("name", "id"),
                result.sourceSchema().columns().stream().map(column -> column.name()).toList());
        assertEquals(
                List.of("display_name", "user_id"),
                result.targetWriteSchema().columns().stream().map(column -> column.name()).toList());
        assertEquals(List.of("id"), result.sourceSchema().primaryKeys());
        assertEquals(List.of("user_id"), result.targetWriteSchema().primaryKeys());
    }

    @Test
    void shouldAllowRealtimePrimaryKeyRename() throws Exception {
        TargetTablePreflight preparer =
                preparer(mappedDataSourceService(), new AtomicInteger());

        DataSyncDefinitionSnapshotVO snapshot = snapshot(false);
        snapshot.setSyncType(DataSyncType.REALTIME.name());
        snapshot.setMapping(mapping(
                columnMapping("name", "display_name"),
                columnMapping("id", "user_id")));

        TargetTablePreflightResult result = preparer.prepare(snapshot, 30);

        assertEquals(List.of("id"), result.sourceSchema().primaryKeys());
        assertEquals(List.of("user_id"), result.targetWriteSchema().primaryKeys());
    }

    @Test
    void shouldRejectRealtimeMappingThatDropsSourcePrimaryKey() throws Exception {
        TargetTablePreflight preparer =
                preparer(mappedDataSourceService(), new AtomicInteger());

        DataSyncDefinitionSnapshotVO snapshot = snapshot(false);
        snapshot.setSyncType(DataSyncType.REALTIME.name());
        snapshot.setMapping(mapping(columnMapping("name", "display_name")));

        DataSyncException exception =
                assertThrows(DataSyncException.class, () -> preparer.prepare(snapshot, 30));

        assertEquals(DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, exception.getErrorCode());
    }

    @Test
    void shouldAutoCreateMappedTargetSchema() throws Exception {
        AtomicBoolean targetExists = new AtomicBoolean(false);
        AtomicReference<LogicalTable> createdTable = new AtomicReference<>();
        TargetTablePreflight preparer =
                preparer(mappedAutoCreateDataSourceService(targetExists), new AtomicInteger());
        inject(preparer, "ddlExecutor", (TargetTableDdlExecutor) (connection, table, schema, timeoutSeconds) -> {
            createdTable.set(schema);
            targetExists.set(true);
            return "CREATE TABLE";
        });

        DataSyncDefinitionSnapshotVO snapshot = snapshot(true);
        snapshot.setMapping(mapping(
                columnMapping("name", "display_name"),
                columnMapping("id", "user_id")));

        TargetTablePreflightResult result = preparer.prepare(snapshot, 30);

        assertTrue(result.targetCreated());
        assertEquals(
                List.of("display_name", "user_id"),
                createdTable.get().columns().stream().map(column -> column.name()).toList());
        assertEquals(List.of("user_id"), createdTable.get().primaryKeys());
        assertEquals("来源用户表", createdTable.get().comment());
        assertEquals(
                List.of("用户名", "用户ID"),
                createdTable.get().columns().stream().map(column -> column.comment()).toList());
        assertEquals(
                List.of("name", "id"),
                result.sourceSchema().columns().stream().map(column -> column.name()).toList());
    }

    @Test
    void shouldRejectExistingIncompatibleTargetSchema() throws Exception {
        AtomicBoolean targetExists = new AtomicBoolean(true);
        TargetTablePreflight preparer =
                preparer(dataSourceService(targetExists, true), new AtomicInteger());

        DataSyncException exception =
                assertThrows(DataSyncException.class, () -> preparer.prepare(snapshot(true), 30));

        assertEquals(DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, exception.getErrorCode());
    }

    private TargetTablePreflight preparer(DataSourceService service, AtomicInteger creates) throws Exception {
        TargetTablePreflight preparer = new TargetTablePreflight();
        inject(preparer, "dataSourceService", service);
        SourceTableIntrospector sourceTableIntrospector = new SourceTableIntrospector();
        inject(sourceTableIntrospector, "dataSourceService", service);
        inject(preparer, "sourceTableIntrospector", sourceTableIntrospector);
        inject(preparer, "targetTablePlanner", new TargetTablePlanner());
        inject(preparer, "ddlExecutor", (TargetTableDdlExecutor) (connection, table, schema, timeoutSeconds) -> {
            creates.incrementAndGet();
            return "CREATE TABLE";
        });
        return preparer;
    }

    private DataSourceService mappedAutoCreateDataSourceService(AtomicBoolean targetExists) {
        DataSourceCatalogColumnVO sourceId = column("id", Types.BIGINT, 19, false, 1, true, 1);
        sourceId.setRemarks("用户ID");
        DataSourceCatalogColumnVO sourceName = column("name", Types.VARCHAR, 100, true, 2, false, null);
        sourceName.setRemarks("用户名");
        List<DataSourceCatalogColumnVO> sourceColumns = List.of(sourceId, sourceName);
        List<DataSourceCatalogColumnVO> targetColumns = List.of(
                column("user_id", Types.BIGINT, 19, false, 1, true, 1),
                column("display_name", Types.VARCHAR, 100, true, 2, false, null));

        return (DataSourceService) Proxy.newProxyInstance(
                DataSourceService.class.getClassLoader(),
                new Class<?>[] {DataSourceService.class},
                (proxy, method, args) -> {
                    String dataSourceId = args != null && args.length > 0 ? String.valueOf(args[0]) : null;
                    if ("queryCatalogTable".equals(method.getName())) {
                        if ("source".equals(dataSourceId)) {
                            DataSourceCatalogTableVO sourceTable = table("source_table");
                            sourceTable.setRemarks("来源用户表");
                            return sourceTable;
                        }
                        return table("target_table");
                    }
                    if ("findCatalogTable".equals(method.getName())) {
                        return targetExists.get() ? Optional.of(table("target_table")) : Optional.empty();
                    }
                    if ("queryCatalogColumns".equals(method.getName())) {
                        return "source".equals(dataSourceId) ? sourceColumns : targetColumns;
                    }
                    if ("resolveRuntimeConnection".equals(method.getName())) {
                        return connection();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSourceService mappedDataSourceService() {
        List<DataSourceCatalogColumnVO> sourceColumns = List.of(
                column("id", Types.BIGINT, 19, false, 1, true, 1),
                column("name", Types.VARCHAR, 100, true, 2, false, null));
        List<DataSourceCatalogColumnVO> targetColumns = List.of(
                column("user_id", Types.BIGINT, 19, false, 1, true, 1),
                column("display_name", Types.VARCHAR, 100, true, 2, false, null));

        return (DataSourceService) Proxy.newProxyInstance(
                DataSourceService.class.getClassLoader(),
                new Class<?>[] {DataSourceService.class},
                (proxy, method, args) -> {
                    String dataSourceId = args != null && args.length > 0 ? String.valueOf(args[0]) : null;
                    if ("queryCatalogTable".equals(method.getName())) {
                        return "source".equals(dataSourceId) ? table("source_table") : table("target_table");
                    }
                    if ("findCatalogTable".equals(method.getName())) {
                        return Optional.of(table("target_table"));
                    }
                    if ("queryCatalogColumns".equals(method.getName())) {
                        return "source".equals(dataSourceId) ? sourceColumns : targetColumns;
                    }
                    if ("resolveRuntimeConnection".equals(method.getName())) {
                        return connection();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSourceService dataSourceService(AtomicBoolean targetExists) {
        return dataSourceService(targetExists, false);
    }

    private DataSourceService dataSourceService(AtomicBoolean targetExists, boolean incompatible) {
        List<DataSourceCatalogColumnVO> sourceColumns = List.of(
                column("id", Types.BIGINT, 19, false, 1, true, 1),
                column("name", Types.VARCHAR, 100, true, 2, false, null));
        List<DataSourceCatalogColumnVO> targetColumns = incompatible
                ? List.of(
                        column("id", Types.BIGINT, 19, false, 1, true, 1),
                        column("name", Types.VARCHAR, 50, false, 2, false, null))
                : List.of(
                        column("id", Types.BIGINT, 19, false, 1, true, 1),
                        column("name", Types.VARCHAR, 100, true, 2, false, null));

        return (DataSourceService) Proxy.newProxyInstance(
                DataSourceService.class.getClassLoader(),
                new Class<?>[] {DataSourceService.class},
                (proxy, method, args) -> {
                    String dataSourceId = args != null && args.length > 0 ? String.valueOf(args[0]) : null;
                    if ("queryCatalogTable".equals(method.getName())) {
                        return table("source_table");
                    }
                    if ("findCatalogTable".equals(method.getName())) {
                        return targetExists.get() ? Optional.of(table("target_table")) : Optional.empty();
                    }
                    if ("queryCatalogColumns".equals(method.getName())) {
                        return "source".equals(dataSourceId) ? sourceColumns : targetColumns;
                    }
                    if ("resolveRuntimeConnection".equals(method.getName())) {
                        return connection();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncDefinitionSnapshotVO snapshot(boolean autoCreateTable) {
        DataSyncDefinitionSnapshotVO snapshot = new DataSyncDefinitionSnapshotVO();
        snapshot.setTaskId("task-1");
        snapshot.setTaskName("task");
        snapshot.setTaskVersion(1);
        snapshot.setSyncType(DataSyncType.OFFLINE.name());
        snapshot.setWriteMode(DataSyncWriteMode.APPEND.name());
        snapshot.setAutoCreateTable(autoCreateTable);
        snapshot.setSource(endpoint("source", "MYSQL", "source_db", null, "source_table"));
        snapshot.setTarget(endpoint("target", "POSTGRE_SQL", "target_db", "public", "target_table"));
        return snapshot;
    }

    private DataSyncMappingVO mapping(DataSyncColumnMappingVO... columns) {
        DataSyncMappingVO mapping = new DataSyncMappingVO();
        mapping.setColumns(List.of(columns));
        return mapping;
    }

    private DataSyncColumnMappingVO columnMapping(String source, String target) {
        DataSyncColumnMappingVO mapping = new DataSyncColumnMappingVO();
        mapping.setSource(source);
        mapping.setTarget(target);
        return mapping;
    }

    private DataSyncEndpointSnapshotVO endpoint(
            String id, String type, String database, String schema, String table) {
        DataSyncEndpointSnapshotVO endpoint = new DataSyncEndpointSnapshotVO();
        endpoint.setDataSourceId(id);
        endpoint.setDataSourceName(id);
        endpoint.setDataSourceType(type);
        endpoint.setDatabase(database);
        endpoint.setSchema(schema);
        endpoint.setTable(table);
        return endpoint;
    }

    private DataSourceCatalogTableVO table(String name) {
        DataSourceCatalogTableVO table = new DataSourceCatalogTableVO();
        table.setName(name);
        table.setType("TABLE");
        return table;
    }

    private DataSourceCatalogColumnVO column(
            String name,
            int jdbcType,
            Integer size,
            boolean nullable,
            int ordinal,
            boolean primaryKey,
            Integer primaryKeyPosition) {
        DataSourceCatalogColumnVO column = new DataSourceCatalogColumnVO();
        column.setName(name);
        column.setTypeName(jdbcType == Types.BIGINT ? "BIGINT" : "VARCHAR");
        column.setJdbcType(jdbcType);
        column.setSize(size);
        column.setScale(jdbcType == Types.BIGINT ? 0 : null);
        column.setNullable(nullable);
        column.setOrdinalPosition(ordinal);
        column.setPrimaryKey(primaryKey);
        column.setPrimaryKeyPosition(primaryKeyPosition);
        return column;
    }

    private DataSourceConnection connection() {
        return new DataSourceConnection() {
            @Override
            public String type() {
                return "POSTGRE_SQL";
            }

            @Override
            public String jdbcUrl() {
                return "jdbc:test";
            }

            @Override
            public String driverClassName() {
                return "test.Driver";
            }

            @Override
            public String username() {
                return "test";
            }

            @Override
            public String password() {
                return "test";
            }

            @Override
            public String database() {
                return "target_db";
            }

            @Override
            public String schema() {
                return "public";
            }

            @Override
            public Map<String, String> properties() {
                return Map.of();
            }

            @Override
            public String normalizedJson() {
                return "{}";
            }
        };
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
