package io.yak.ops.boot.integration.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Ensures datasource validation remains bound to an explicit Workspace identity. */
class DataSourceTaskValidationContextFactoryTest {
    @Test
    void bindsDatasourceIdentityToWorkspaceWithoutAnHttpRequest() {
        AtomicReference<String> observedWorkspace = new AtomicReference<>();
        DataSourceLookupService resources = new DataSourceLookupService() {
            @Override
            public ReadonlyConfig connectionOptions(String workspaceId, String datasourceId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ReadonlyConfig connectionOptions(String workspaceId, String datasourceId, long expectedRevision) {
                throw new UnsupportedOperationException();
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
        assertEquals("MYSQL", first.dataSource("source").type());
        assertEquals("workspace-a", observedWorkspace.get());
        assertEquals("MYSQL", second.dataSource("source").type());
        assertEquals("workspace-b", observedWorkspace.get());
        assertTrue(first.samePhysicalServer("source", "source-alias"));
        assertEquals("workspace-a", observedWorkspace.get());
        assertTrue(second.samePhysicalServer("source", "source-alias"));
        assertEquals("workspace-b", observedWorkspace.get());
    }

    private static void inject(DataSourceTaskValidationContextFactory factory, DataSourceLookupService resources) {
        try {
            Field field = DataSourceTaskValidationContextFactory.class.getDeclaredField("resources");
            field.setAccessible(true);
            field.set(factory, resources);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
