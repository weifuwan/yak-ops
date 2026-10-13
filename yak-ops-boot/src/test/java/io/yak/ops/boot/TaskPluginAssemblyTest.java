package io.yak.ops.boot;

import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.configuration.ReadonlyConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.yak.ops.boot.config.TaskPluginConfiguration;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.datasync.DataSyncTaskPlugin;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Verifies Boot constructor assembly of one Task registry and DATA_SYNC execution capability. */
class TaskPluginAssemblyTest {

    @Test
    void loadsTaskPluginThroughBootConfiguration() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                TaskPluginConfiguration.class, TestDatasourceLookup.class)) {
            TaskPluginRegistry registry = context.getBean(TaskPluginRegistry.class);
            assertEquals(1, registry.types().size());
            assertInstanceOf(ExecutableTaskPlugin.class, registry.get(DataSyncTaskPlugin.TYPE));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestDatasourceLookup {
        @Bean
        DataSourceLookupService datasourceLookup() {
            return new DataSourceLookupService() {
                @Override
                public DataSourceTable table(
                        String workspaceId,
                        String datasourceId,
                        String database,
                        String schema,
                        String table) {
                    throw new UnsupportedOperationException("Connection planning requires authorized product resources");
                }

                @Override
                public ReadonlyConfig connectionOptions(String workspaceId, String datasourceId) {
                    throw new UnsupportedOperationException("Connection resolution requires authorized product resources");
                }

                @Override
                public ReadonlyConfig connectionOptions(String workspaceId, String datasourceId, long expectedRevision) {
                    throw new AssertionError("Connection settings are not used by this test");
                }

                @Override
                public DataSourceInfo dataSource(String workspaceId, String datasourceId) {
                    throw new AssertionError("Datasource lookup is not used by this test");
                }
            };
        }
    }
}
