package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.plugin.task.datasync.config.JobConfig;
import io.yak.ops.plugin.task.datasync.config.JobOptions;
import io.yak.ops.plugin.task.datasync.plan.JobPlanner;
import io.yak.ops.plugin.task.datasync.execution.DataSyncTaskExecutor;
import io.yak.ops.plugin.task.datasync.pipeline.PipelineBuilder;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import java.nio.file.Path;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Verifies stateless plugin discovery and independent local and physical validation boundaries. */
class DataSyncTaskPluginContractTest {
    private final TaskPluginRegistry registry = new TaskPluginRegistry(List.of(plugin()));

    @Test
    void registersBootConstructedPluginWithOneRegistry() {
        assertTrue(registry.types().contains("DATA_SYNC"));
        assertInstanceOf(DataSyncTaskPlugin.class, registry.get("DATA_SYNC"));
        assertEquals(2, registry.get("DATA_SYNC").parameterSchemaVersion());
        assertThrows(TaskPluginException.class, () -> registry.get("data-sync"));
        assertThrows(TaskPluginException.class, () -> registry.get("data_sync"));
        assertThrows(TaskPluginException.class, () -> registry.get("SQL"));
        assertThrows(TaskPluginException.class, () -> new TaskPluginRegistry(List.of(plugin(), plugin())));
    }

    @Test
    void parsesJDBCAndMySqlCdcWithoutAddingExecutionState() {
        ReadonlyConfig jdbc = registry.get("DATA_SYNC").parse(offlineJson());
        ReadonlyConfig cdc = registry.get("DATA_SYNC").parse(realtimeJson());
        assertEquals("JDBC", JobConfig.from(jdbc).source().get(JobOptions.TYPE));
        assertEquals("MYSQL_CDC", JobConfig.from(cdc).source().get(JobOptions.TYPE));
        assertEquals("JDBC", JSONUtils.readTree(ConfigParser.toJson(jdbc)).path("source").path("type").asText());
        assertEquals("MYSQL_CDC", JSONUtils.readTree(ConfigParser.toJson(cdc)).path("source").path("type").asText());
        assertEquals(3, JSONUtils.readTree(ConfigParser.toJson(jdbc)).size());
        assertEquals(jdbc, registry.get("DATA_SYNC").parse(ConfigParser.toJson(jdbc)));
    }

    @Test
    void rejectsUnsupportedModesAndUndeclaredParametersOnParse() {
        for (String json : List.of("{}", "[]", "{", offlineJson().replace("JDBC", "KAFKA"),
                offlineJson().replace("\"transform\": []", "\"transform\": [{}]"),
                offlineJson().replace("\"transform\": []", "\"runtimeConfig\": {}"),
                offlineJson().replace("APPEND", "OVERWRITE"), realtimeJson().replace("UPSERT", "APPEND"))) {
            assertThrows(TaskPluginException.class, () -> registry.get("DATA_SYNC").parse(json));
        }
    }

    @Test
    void rejectsSecretFieldsWithoutEchoingTheirValues() {
        String json = offlineJson().replace("\"table\":\"orders\"", "\"table\":\"orders\",\"password\":\"private-value\"");
        TaskPluginException exception = assertThrows(TaskPluginException.class, () -> registry.get("DATA_SYNC").parse(json));
        assertEquals("Invalid DATA_SYNC pipeline JSON", exception.getMessage());
    }

    @Test
    void rejectsConnectionAndPreparedReplicationFieldsInDefinitions() {
        for (String key : List.of("connection.url", "connection.password", "connector.mysql-cdc.server-id", "connector.mysql-cdc.topic-prefix")) {
            String json = offlineJson().replace("\"table\":\"orders\"", "\"table\":\"orders\",\"" + key + "\":\"private-value\"");
            TaskPluginException failure = assertThrows(TaskPluginException.class, () -> registry.get("DATA_SYNC").parse(json));
            assertTrue(!failure.toString().contains("private-value"));
        }
    }

    @Test
    void draftValidationChecksAuthorizedReferencesWithoutReadingTableSchemas() {
        TaskPlugin plugin = registry.get("DATA_SYNC");
        ReadonlyConfig parameters = plugin.parse(realtimeJson());
        TaskValidationContext context = new TaskValidationContext() {
            @Override
            public DataSourceInfo dataSource(String id) {
                return "source".equals(id) ? new DataSourceInfo("MYSQL", "app", null) : new DataSourceInfo("POSTGRE_SQL", null, "ods");
            }
            @Override
            public TableSchema tableSchema(String datasourceId, TableId tableId) {
                throw new AssertionError("Draft validation must not request external table metadata");
            }
        };
        plugin.validateReferences(parameters, context);
        assertEquals(null, JobConfig.from(parameters).source().get(JobOptions.DATABASE));
        assertEquals(null, JobConfig.from(parameters).sink().get(JobOptions.SCHEMA));
    }

    @Test
    void validatesAuthorizedMetadataWithoutRewritingExplicitTablePaths() {
        Metadata context = new Metadata();
        ReadonlyConfig parameters = parseAndValidate(realtimeJson(), context);
        assertEquals("MYSQL_CDC", JobConfig.from(parameters).source().get(JobOptions.TYPE));
        assertEquals(null, JobConfig.from(parameters).source().get(JobOptions.DATABASE));
        assertEquals(null, JobConfig.from(parameters).sink().get(JobOptions.SCHEMA));
        assertEquals("app", context.lastSourceTable.catalog());
        assertEquals("ods", context.lastTargetTable.schema());
        ReadonlyConfig bounded = parseAndValidate(offlineJson(), context);
        assertEquals("JDBC", JobConfig.from(bounded).source().get(JobOptions.TYPE));
    }

    @Test
    void rejectsConflictsWithDatasourceBoundDatabaseAndSchema() {
        Metadata context = new Metadata();
        String otherDatabase = offlineJson().replace("\"datasourceId\":\"source\",\"table\"", "\"datasourceId\":\"source\",\"database\":\"wrong_db\",\"table\"");
        assertThrows(TaskPluginException.class, () -> parseAndValidate(otherDatabase, context));
        String otherSchema = offlineJson().replace("\"datasourceId\":\"target\",\"table\"", "\"datasourceId\":\"target\",\"schema\":\"wrong_schema\",\"table\"");
        assertThrows(TaskPluginException.class, () -> parseAndValidate(otherSchema, context));
    }

    @Test
    void checksCrossDatabaseColumnAndPrimaryKeyCompatibility() {
        Metadata wrong = new Metadata() {
            @Override
            public TableSchema tableSchema(String id, TableId tableId) {
                if ("target".equals(id)) {
                    return new TableSchema(List.of(new Column("other", LogicalTypes.INTEGER)), List.of("other"));
                }
                return super.tableSchema(id, tableId);
            }
        };
        assertThrows(TaskPluginException.class, () -> parseAndValidate(realtimeJson(), wrong));
    }

    @Test
    void pluginParsesAndValidatesEachDefinitionExactlyOnce() {
        AtomicInteger parses = new AtomicInteger();
        AtomicInteger validations = new AtomicInteger();
        TaskPlugin plugin = new TaskPlugin() {
            @Override
            public String type() { return "TEST"; }
            @Override
            public int parameterSchemaVersion() { return 1; }
            @Override
            public OptionRule optionRule() { return OptionRule.builder().build(); }
            @Override
            public ReadonlyConfig parse(String content) {
                parses.incrementAndGet();
                return ReadonlyConfig.fromMap(java.util.Map.of());
            }
            @Override
            public void validateReferences(ReadonlyConfig parameters, TaskValidationContext context) {}
            @Override
            public void validate(ReadonlyConfig parameters, TaskValidationContext context) { validations.incrementAndGet(); }
        };
        TaskPlugin registered = new TaskPluginRegistry(List.of(plugin)).get("TEST");
        ReadonlyConfig parameters = registered.parse("{}");
        registered.validate(parameters, new Metadata());
        assertEquals(1, parses.get());
        assertEquals(1, validations.get());
    }

    private static DataSyncTaskPlugin plugin() {
        DataSourceLookupService unsupported = new DataSourceLookupService() {
            @Override
            public DataSourceTable table(String workspace, String id, String database, String schema, String name) {
                throw new UnsupportedOperationException("Physical resource access is disabled in definition tests");
            }
            @Override
            public ReadonlyConfig connectionOptions(String workspace, String id) {
                throw new UnsupportedOperationException("Physical resource access is disabled in definition tests");
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
        PipelineExecutor executor = new EmbeddedPipelineExecutor();
        FactoryUtil factories = new FactoryUtil();
        JobPlanner planner = new JobPlanner(unsupported, factories);
        PipelineBuilder builder = new PipelineBuilder(unsupported, factories, Path.of("test-state"));
        return new DataSyncTaskPlugin(planner, new DataSyncTaskExecutor(planner, builder, executor), factories);
    }

    private ReadonlyConfig parseAndValidate(String json, TaskValidationContext context) {
        TaskPlugin plugin = registry.get("DATA_SYNC");
        ReadonlyConfig parameters = plugin.parse(json);
        plugin.validate(parameters, context);
        return parameters;
    }

    static String offlineJson() {
        return """
                {
                  "source": {"type":"JDBC","datasourceId":"source","table":"orders"},
                  "transform": [],
                  "sink": {"type":"JDBC","datasourceId":"target","table":"orders_copy","writeMode":"APPEND"}
                }
                """;
    }
    static String realtimeJson() {
        return """
                {
                  "source": {"type":"MYSQL_CDC","datasourceId":"source","table":"orders"},
                  "transform": [],
                  "sink": {"type":"JDBC","datasourceId":"target","table":"orders_copy","writeMode":"UPSERT"}
                }
                """;
    }
    static class Metadata implements TaskValidationContext {
        TableId lastSourceTable;
        TableId lastTargetTable;
        @Override
        public DataSourceInfo dataSource(String id) {
            if ("source".equals(id)) { return new DataSourceInfo("MYSQL", "app", null); }
            return new DataSourceInfo("POSTGRE_SQL", null, "ods");
        }
        @Override
        public TableSchema tableSchema(String id, TableId tableId) {
            if ("source".equals(id)) { lastSourceTable = tableId; } else { lastTargetTable = tableId; }
            return new TableSchema(List.of(new Column("id", LogicalTypes.BIGINT), new Column("name", LogicalTypes.STRING)), List.of("id"));
        }
    }
}
