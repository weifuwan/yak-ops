package io.yak.ops.boot.integration.task;

import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.configuration.ReadonlyConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Verifies background Task validation resolves metadata through explicit Workspace scope. */
class DataSourceTaskValidationContextFactoryTest {

    @Test
    void bindsDatasourceMetadataToWorkspaceWithoutAnHttpRequest() {
        TableSchema schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        AtomicReference<String> observedWorkspace = new AtomicReference<>();
        DataSourceLookupService resources = new DataSourceLookupService() {
            @Override
            public DataSourceTable table(
                    String workspaceId, String datasourceId, String database, String schemaName, String table) {
                observedWorkspace.set(workspaceId);
                assertEquals("source", datasourceId);
                return new DataSourceTable("MYSQL", new TableId(database, schemaName, table), schema, 1);
            }

            @Override
            public ReadonlyConfig connectionOptions(String workspaceId, String datasourceId) {
                throw new UnsupportedOperationException("Connection resolution is not needed by this test");
            }

            @Override
            public ReadonlyConfig connectionOptions(String workspaceId, String datasourceId, long expectedRevision) {
                throw new AssertionError("Connection settings are not used by this test");
            }

            @Override
            public DataSourceInfo dataSource(String workspaceId, String datasourceId) {
                observedWorkspace.set(workspaceId);
                return new DataSourceInfo("MYSQL", "app", null, 1);
            }

            @Override
            public boolean samePhysicalServer(String workspaceId, String first, String second) {
                observedWorkspace.set(workspaceId);
                assertEquals("source", first);
                assertEquals("source-alias", second);
                return true;
            }
        };
        DataSourceTaskValidationContextFactory factory = new DataSourceTaskValidationContextFactory();
        inject(factory, resources);

        var first = factory.forWorkspace("workspace-a");
        var second = factory.forWorkspace("workspace-b");
        TableId tableId = new TableId("app", null, "orders");
        assertEquals("MYSQL", first.dataSource("source").type());
        assertEquals("workspace-a", observedWorkspace.get());

        assertSame(schema, first.tableSchema("source", tableId));
        assertEquals("workspace-a", observedWorkspace.get());
        assertSame(schema, second.tableSchema("source", tableId));
        assertEquals("workspace-b", observedWorkspace.get());
        assertTrue(first.samePhysicalServer("source", "source-alias"));
        assertEquals("workspace-a", observedWorkspace.get());
        assertTrue(second.samePhysicalServer("source", "source-alias"));
        assertEquals("workspace-b", observedWorkspace.get());
    }

    private void inject(DataSourceTaskValidationContextFactory factory, DataSourceLookupService resources) {
        try {
            Field field = DataSourceTaskValidationContextFactory.class.getDeclaredField("resources");
            field.setAccessible(true);
            field.set(factory, resources);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }
}
