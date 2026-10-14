package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.task.datasync.config.JobConfig;
import io.yak.ops.plugin.task.datasync.config.JobOptions;
import io.yak.ops.plugin.task.datasync.solution.SolutionType;
import io.yak.ops.plugin.task.datasync.solution.Step;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Public product configuration, explicit persisted formats, and source/target capability rules. */
class DataSyncProductParametersTest {
    private final DataSyncTaskPlugin plugin = DataSyncTaskPluginContractTest.plugin();

    @Test
    void v3RemainsThePersistedFormatWhileFactoriesUseTheResolvedProjection() {
        var parameters = plugin.parse(json(false));
        var config = JobConfig.from(parameters);
        assertEquals(SolutionType.SINGLE_TABLE_OFFLINE, config.solutionType());
        assertEquals("JDBC", config.source().get(JobOptions.TYPE));
        assertEquals("orders", config.source().get(JobOptions.TABLE));
        assertEquals("copy", config.sink().get(JobOptions.TABLE));
        String stored = ConfigParser.toJson(parameters);
        assertTrue(stored.contains("tableMappings"));
        assertFalse(stored.contains("JDBC"));
        assertEquals(parameters, plugin.parse(3, stored));
        assertTrue(plugin.supportsScheduling(parameters));
        assertFalse(plugin.supportsScheduling(plugin.parse(json(true))));
    }

    @Test
    void onlyTheTwoCurrentDirectedSolutionsAreAdvertisedWithLegalDefaults() {
        var mysql = plugin.solutions("MYSQL", "POSTGRE_SQL");
        assertEquals(List.of(SolutionType.SINGLE_TABLE_OFFLINE, SolutionType.SINGLE_TABLE_REALTIME),
                mysql.stream().map(value -> value.solutionType()).toList());
        assertEquals(List.of(Step.FULL, Step.INCREMENTAL), mysql.get(1).defaultSteps());
        assertEquals(List.of("UPSERT"), mysql.get(1).allowedCombinations().getFirst().writeModes());
        assertFalse(mysql.get(1).allowedCombinations().getFirst().supportsScheduling());
        assertEquals(List.of(SolutionType.SINGLE_TABLE_OFFLINE), plugin.solutions("POSTGRE_SQL", "MYSQL")
                .stream().map(value -> value.solutionType()).toList());
        assertEquals(1, plugin.solutions("ORACLE", "ORACLE").size());
        assertThrows(IllegalArgumentException.class, () -> plugin.solutions("JDBC", "MYSQL"));
        assertThrows(IllegalArgumentException.class, () -> plugin.solutions("MYSQL", ""));
        assertThrows(UnsupportedOperationException.class, () -> mysql.clear());
    }

    @Test
    void storedV2IsExplicitlyReadableButCannotBeSubmittedAsANewDefinition() {
        String stored = DataSyncTaskPluginContractTest.offlineJson();
        var old = plugin.parse(2, stored);
        assertEquals("JDBC", JobConfig.from(old).source().get(JobOptions.TYPE));
        assertFalse(ConfigParser.toJson(old).contains("solutionType"));
        assertThrows(TaskPluginException.class, () -> plugin.parse(stored));
        assertThrows(TaskPluginException.class, () -> plugin.parse(3, stored));
        assertThrows(TaskPluginException.class, () -> plugin.parse(2, json(false)));
        assertThrows(TaskPluginException.class, () -> plugin.parse(1, stored));
    }

    @Test
    void rejectsUnsupportedStepsAndMixedFormatsInsteadOfSilentlyChangingTheSolution() {
        String realtime = json(true);
        for (String invalid : List.of(
                realtime.replace("[\"FULL\",\"INCREMENTAL\"]", "[]"),
                realtime.replace("[\"FULL\",\"INCREMENTAL\"]", "[\"INCREMENTAL\"]"),
                realtime.replace("[\"FULL\",\"INCREMENTAL\"]", "[\"FULL\",\"FULL\"]"),
                realtime.replace("[\"FULL\",\"INCREMENTAL\"]", "[\"SCHEMA\",\"FULL\",\"INCREMENTAL\"]"),
                realtime.replace("EXPLICIT", "PATTERN"),
                realtime.replace("SINGLE_TABLE_REALTIME", "DATABASE_REALTIME"),
                realtime.replace("UPSERT", "APPEND"),
                realtime.replace("UPSERT", "OVERWRITE"),
                realtime.replace("\"source\":{\"datasourceId\"", "\"source\":{\"type\":\"JDBC\",\"datasourceId\""),
                realtime.replace("\"table\":\"orders\"", "\"table\":\"orders\",\"password\":\"hidden-value\""))) {
            TaskPluginException failure = assertThrows(TaskPluginException.class, () -> plugin.parse(invalid));
            assertFalse(failure.getMessage().contains("hidden-value"));
        }
    }

    @Test
    void normalizesStepOrderButDoesNotModifyChosenWriteMode() {
        var parsed = plugin.parse(json(true).replace("[\"FULL\",\"INCREMENTAL\"]", "[\"INCREMENTAL\",\"FULL\"]"));
        assertEquals(List.of("FULL", "INCREMENTAL"), parsed.getSourceMap().get("steps"));
        assertEquals("UPSERT", JobConfig.from(parsed).sink().getSourceMap().get("writeMode"));
    }

    @Test
    void rejectsAnUnexpectedMappingCardinality() {
        String json = json(false);
        var root = ConfigParser.parseJson(json).getSourceMap();
        var changed = new java.util.LinkedHashMap<>(root);
        changed.put("tableMappings", List.of());
        assertThrows(TaskPluginException.class, () -> plugin.parse(ConfigParser.toJson(io.yak.ops.configuration.ReadonlyConfig.fromMap(changed))));
        List<?> tables = (List<?>) root.get("tableMappings");
        changed.put("tableMappings", List.of(tables.getFirst(), tables.getFirst()));
        assertThrows(TaskPluginException.class, () -> plugin.parse(ConfigParser.toJson(io.yak.ops.configuration.ReadonlyConfig.fromMap(changed))));
    }

    @Test
    void validatesDatasourceTypesAndKeepsTheSavedSolutionIdentity() {
        TaskValidationContext context = new DataSyncTaskPluginContractTest.Metadata() {
            @Override
            public DataSourceInfo dataSource(String id) {
                if ("oracle".equals(id)) return new DataSourceInfo("ORACLE", null, "SCHEMA");
                return super.dataSource(id);
            }

            @Override
            public io.yak.ops.core.types.TableSchema tableSchema(String id, io.yak.ops.core.data.TableId table) {
                throw new AssertionError("Reference validation does not read external table metadata");
            }
        };
        var offline = plugin.parse(json(false));
        plugin.validateReferences(offline, context);
        plugin.validateUpdate(plugin.parse(2, DataSyncTaskPluginContractTest.offlineJson()), offline, context);
        assertThrows(TaskPluginException.class, () -> plugin.validateUpdate(offline, plugin.parse(json(true)), context));
        var changed = plugin.parse(json(false).replace("\"target\"}", "\"oracle\"}"));
        assertThrows(TaskPluginException.class, () -> plugin.validateUpdate(offline, changed, context));
        var cdcOracle = plugin.parse(json(true).replace("\"source\"}", "\"oracle\"}"));
        assertThrows(TaskPluginException.class, () -> plugin.validateReferences(cdcOracle, context));
    }

    static String json(boolean realtime) {
        String type = "SINGLE_TABLE_OFFLINE";
        String steps = "[\"FULL\"]";
        String write = "APPEND";
        if (realtime) { type = "SINGLE_TABLE_REALTIME"; steps = "[\"FULL\",\"INCREMENTAL\"]"; write = "UPSERT"; }
        return """
                {"solutionType":"%s","steps":%s,
                 "source":{"datasourceId":"source"},"sink":{"datasourceId":"target"},
                 "tableSelection":{"mode":"EXPLICIT"},
                 "tableMappings":[{"source":{"table":"orders"},"target":{"table":"copy"},"writeMode":"%s"}]}
                """.formatted(type, steps, write);
    }
}
