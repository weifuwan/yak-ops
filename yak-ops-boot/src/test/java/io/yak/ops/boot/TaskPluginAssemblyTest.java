package io.yak.ops.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.yak.ops.boot.config.TaskPluginConfiguration;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskPluginManager;
import io.yak.ops.plugin.task.datasync.PipelineConnectorCatalog;
import io.yak.ops.plugin.task.datasync.PipelineTaskChannel;
import java.util.List;
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
            TaskPluginManager registry = context.getBean(TaskPluginManager.class);
            assertEquals(1, registry.types().size());
            TaskChannel channel = assertInstanceOf(TaskChannel.class, registry.get(PipelineTaskChannel.TYPE));
            assertEquals(7, channel.parameterSchemaVersion());
            PipelineConnectorCatalog catalog = context.getBean(PipelineConnectorCatalog.class);
            List<PipelineConnectorCatalog.ConnectorPair> pairs = catalog.list("MYSQL", "POSTGRE_SQL");
            assertEquals(List.of("jdbc-mysql", "mysql"),
                    pairs.stream().map(PipelineConnectorCatalog.ConnectorPair::sourceConnector).sorted().toList());
            assertEquals(List.of("jdbc-postgres"),
                    pairs.stream().map(PipelineConnectorCatalog.ConnectorPair::sinkConnector).distinct().toList());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestDatasourceLookup {
        @Bean
        io.yak.ops.plugin.task.api.execution.TaskCursorStore cursors() {
            return (identity, batches) -> batches;
        }

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
