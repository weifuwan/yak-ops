package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceOptions;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.task.datasync.config.JobConfig;
import io.yak.ops.plugin.task.datasync.config.JobOptions;
import io.yak.ops.plugin.task.datasync.solution.SolutionType;
import io.yak.ops.plugin.task.datasync.solution.Step;
import io.yak.ops.plugin.task.datasync.validation.SchemaValidator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** One V4 product contract, strict options and verified physical row identity. */
class DataSyncProductParametersTest {
    private final DataSyncTaskPlugin plugin = DataSyncTaskPluginContractTest.plugin();

    @Test
    void currentContractKeepsOnlyComponentOwnedFieldsAndDerivedConnectorOptions() {
        var parameters = plugin.parse(json(false));
        var config = JobConfig.from(parameters);
        assertEquals(SolutionType.SINGLE_TABLE_OFFLINE, config.solutionType());
        assertEquals("JDBC", config.source().get(JobOptions.TYPE));
        assertEquals("orders", config.source().get(JobOptions.TABLE));
        assertEquals("copy", config.sink().get(JobOptions.TABLE));
        String stored = ConfigParser.toJson(parameters);
        assertTrue(stored.contains("\"tables\""));
        assertFalse(stored.contains("tableMappings"));
        assertFalse(stored.contains("\"type\":\"JDBC\""));
        assertEquals(parameters, plugin.parse(4, stored));
        assertTrue(plugin.supportsScheduling(parameters));
        assertFalse(plugin.supportsScheduling(plugin.parse(json(true))));
    }

    @Test
    void supportedSolutionsStillRespectDirectedConnectorCapabilities() {
        var mysql = plugin.solutions("MYSQL", "POSTGRE_SQL");
        assertEquals(List.of(SolutionType.SINGLE_TABLE_OFFLINE, SolutionType.SINGLE_TABLE_REALTIME,
                        SolutionType.DATABASE_OFFLINE, SolutionType.DATABASE_REALTIME),
                mysql.stream().map(value -> value.solutionType()).toList());
        assertEquals(List.of(Step.FULL, Step.INCREMENTAL), mysql.get(1).defaultSteps());
        assertEquals(List.of("UPSERT", "RETRACT"),
                plugin.solutions("MYSQL", "MYSQL").get(1).allowedCombinations().getFirst().writeModes());
        assertEquals(List.of(SolutionType.SINGLE_TABLE_OFFLINE, SolutionType.DATABASE_OFFLINE),
                plugin.solutions("POSTGRE_SQL", "MYSQL").stream().map(value -> value.solutionType()).toList());
        assertThrows(IllegalArgumentException.class, () -> plugin.solutions("JDBC", "MYSQL"));
    }

    @Test
    void rejectsOldSchemaVersionsAndLegacyPipelineInsteadOfConvertingThem() {
        assertThrows(TaskPluginException.class, () -> plugin.parse(2, json(false)));
        assertThrows(TaskPluginException.class, () -> plugin.parse(3, json(false)));
        assertThrows(TaskPluginException.class, () -> plugin.parse(DataSyncTaskPluginContractTest.offlineJson()
                .replace("\"solutionType\":\"SINGLE_TABLE_OFFLINE\",", "")));
        assertThrows(TaskPluginException.class, () -> plugin.parse(json(false)
                .replace("\"env\":{}", "\"tableSelection\":{\"mode\":\"EXPLICIT\"},\"env\":{}")));
    }

    @Test
    void rejectsUnsupportedStepsUnknownComponentsAndWriteModes() {
        String realtime = json(true);
        for (String invalid : List.of(
                realtime.replace("[\"FULL\",\"INCREMENTAL\"]", "[]"),
                realtime.replace("[\"FULL\",\"INCREMENTAL\"]", "[\"INCREMENTAL\"]"),
                realtime.replace("[\"FULL\",\"INCREMENTAL\"]", "[\"FULL\",\"FULL\"]"),
                realtime.replace("[\"FULL\",\"INCREMENTAL\"]", "[\"SCHEMA\",\"FULL\"]"),
                realtime.replace("SINGLE_TABLE_REALTIME", "UNKNOWN"),
                realtime.replace("\"mode\":\"UPSERT\"", "\"mode\":\"APPEND\""),
                realtime.replace("\"mode\":\"UPSERT\"", "\"mode\":\"OVERWRITE\""),
                realtime.replace("\"tables\":[", "\"type\":\"JDBC\",\"tables\":["),
                realtime.replace("\"table\":\"orders\"", "\"table\":\"orders\",\"password\":\"private\""))) {
            TaskPluginException failure = assertThrows(TaskPluginException.class, () -> plugin.parse(invalid));
            assertFalse(failure.getMessage().contains("private"));
        }
    }

    @Test
    void rejectsDuplicateRoutesAndUnreferencedSinkInputs() {
        String json = json(false);
        assertThrows(TaskPluginException.class, () -> plugin.parse(json.replace("\"id\":\"orders\"",
                "\"id\":\"orders\",\"unknown\":true")));
        assertThrows(TaskPluginException.class, () -> plugin.parse(json.replace("\"input\":\"orders\"",
                "\"input\":\"missing\"")));
        assertThrows(TaskPluginException.class, () -> plugin.parse(json.replace("\"tables\":[{\"id\":\"orders\"",
                "\"tables\":[]")));
    }

    @Test
    void validatesDatasourceTypesAndDoesNotReadExternalSchemasForReferenceChecks() {
        TaskValidationContext context = new DataSyncTaskPluginContractTest.Metadata() {
            @Override
            public DataSourceInfo dataSource(String id) {
                if ("oracle".equals(id)) return new DataSourceInfo("ORACLE", null, "SCHEMA", 1);
                return super.dataSource(id);
            }

            @Override
            public io.yak.ops.core.types.TableSchema tableSchema(
                    String id, io.yak.ops.core.data.TableId table) {
                throw new AssertionError("Reference validation must not access table metadata");
            }
        };
        var offline = plugin.parse(json(false));
        plugin.validateReferences(offline, context);
        plugin.validateUpdate(offline, offline, context);
        assertThrows(TaskPluginException.class,
                () -> plugin.validateUpdate(offline, plugin.parse(json(true)), context));
        var newSink = plugin.parse(json(false).replace("\"datasourceId\":\"target\"",
                "\"datasourceId\":\"oracle\""));
        assertThrows(TaskPluginException.class, () -> plugin.validateUpdate(offline, newSink, context));
    }

    @Test
    void keylessXaIsSinkOwnedAndSnapshotKeyIsSourceOwned() {
        String keyless = keyless("RETRACT", "\"transaction\":{\"mode\":\"CHECKPOINT_XA\"}");
        var config = JobConfig.from(plugin.parse(keyless));
        assertTrue(config.source().get(MySqlCdcSourceOptions.KEYLESS_ENABLED));
        assertEquals("bucket", config.source().get(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN));
        assertTrue(config.sink().get(JdbcSinkOptions.XA_ENABLED));
        assertFalse(config.source().getSourceMap().containsKey("xa"));
        assertThrows(TaskPluginException.class, () -> plugin.parse(keyless.replace(
                ",\"transaction\":{\"mode\":\"CHECKPOINT_XA\"}", "")));
        assertThrows(TaskPluginException.class, () -> plugin.parse(keyless.replace("CHECKPOINT_XA", "NONE")));
        assertThrows(TaskPluginException.class, () -> plugin.parse(keyless.replace("RETRACT", "APPEND")));
        assertThrows(TaskPluginException.class, () -> plugin.parse(keyless.replace("bucket", "")));
    }

    @Test
    void verifiedTargetIdentityNeverBecomesSourcePrimaryKey() {
        var columns = List.of(
                new io.yak.ops.core.types.Column(
                        "bucket", io.yak.ops.core.types.LogicalTypes.BIGINT.copy(false)),
                new io.yak.ops.core.types.Column(
                        "amount", io.yak.ops.core.types.LogicalTypes.decimal(12, 2)));
        var physical = new io.yak.ops.core.types.TableSchema(columns, List.of());
        var source = new DataSourceTable(
                "MYSQL", new io.yak.ops.core.data.TableId("app", null, "orders"),
                physical, 1, null, Map.of(), Set.of(), Map.of(), List.of(List.of("bucket")));
        var write = SchemaValidator.writeSchema(source, List.of("bucket"));
        assertEquals(List.of(), source.schema().primaryKeys());
        assertEquals(List.of("bucket"), write.primaryKeys());
        assertThrows(IllegalArgumentException.class,
                () -> SchemaValidator.writeSchema(source, List.of("amount")));
        var unsafe = new DataSourceTable("MYSQL", source.table(), physical, 1);
        assertThrows(IllegalArgumentException.class,
                () -> SchemaValidator.writeSchema(unsafe, List.of("bucket")));
    }

    @Test
    void initialSchemaAndTargetKeysAreSeparateFromSnapshotIdentity() {
        String config = keyless("UPSERT", "\"primaryKeys\":[\"bucket\"]")
                .replace("\"initial\":\"VERIFY\"", "\"initial\":\"CREATE_OR_ALTER\"");
        var job = JobConfig.from(plugin.parse(config));
        assertEquals(List.of("bucket"), job.tables().getFirst().targetPrimaryKeys());
        assertEquals(List.of(Step.SCHEMA, Step.FULL, Step.INCREMENTAL), job.steps());
        assertFalse(job.sink().get(JdbcSinkOptions.XA_ENABLED));
        assertThrows(TaskPluginException.class,
                () -> plugin.parse(config.replace("\"primaryKeys\":[\"bucket\"]",
                        "\"primaryKeys\":[\"bucket\",\"bucket\"]")));
    }

    static String json(boolean realtime) {
        String type = realtime ? "SINGLE_TABLE_REALTIME" : "SINGLE_TABLE_OFFLINE";
        String steps = realtime ? "[\"FULL\",\"INCREMENTAL\"]" : "[\"FULL\"]";
        String mode = realtime ? "UPSERT" : "APPEND";
        return """
                {"solutionType":"%s","steps":%s,
                 "source":{"datasourceId":"source","tables":[{"id":"orders","table":"orders"}]},
                 "transform":[],
                 "sink":{"datasourceId":"target",
                         "schemaPolicy":{"initial":"VERIFY","evolution":{"enabled":false,"allowDropColumns":false}},
                         "tables":[{"input":"orders","table":"copy","write":{"mode":"%s"}}]},
                 "env":{}}
                """.formatted(type, steps, mode);
    }

    static String keyless(String mode, String writeExtra) {
        return """
                {"solutionType":"SINGLE_TABLE_REALTIME","steps":["FULL","INCREMENTAL"],
                 "source":{"datasourceId":"source","tables":[{"id":"orders","table":"orders",
                           "keyless":true,"snapshot":{"chunkKeyColumn":"bucket"}}]},
                 "transform":[],
                 "sink":{"datasourceId":"target",
                         "schemaPolicy":{"initial":"VERIFY","evolution":{"enabled":false,"allowDropColumns":false}},
                         "tables":[{"input":"orders","table":"copy",
                                    "write":{"mode":"%s",%s}}]},
                 "env":{}}
                """.formatted(mode, writeExtra);
    }
}
