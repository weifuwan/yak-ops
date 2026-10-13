package io.yak.ops.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.yak.ops.boot.config.TaskPluginConfiguration;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.datasync.SyncTaskPlugin;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeConnection;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeResources;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeTable;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Verifies Boot constructor assembly of one Task registry and DATA_SYNC execution capability. */
class TaskPluginAssemblyTest {

    @Test
    void loadsTaskPluginThroughBootConfiguration() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                TaskPluginConfiguration.class, TestRuntimeResources.class)) {
            TaskPluginRegistry registry = context.getBean(TaskPluginRegistry.class);
            assertEquals(1, registry.types().size());
            assertInstanceOf(ExecutableTaskPlugin.class, registry.get(SyncTaskPlugin.TYPE));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestRuntimeResources {
        @Bean
        SyncRuntimeResources syncRuntimeResources() {
            return new SyncRuntimeResources() {
                @Override
                public SyncRuntimeTable table(
                        String workspaceId,
                        String datasourceId,
                        String database,
                        String schema,
                        String table) {
                    throw new UnsupportedOperationException("Connection planning requires authorized product resources");
                }

                @Override
                public SyncRuntimeConnection connection(String workspaceId, String datasourceId) {
                    throw new UnsupportedOperationException("Connection resolution requires authorized product resources");
                }
            };
        }
    }
}
