package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.*;
import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.datasync.config.JobConfig;
import io.yak.ops.plugin.task.datasync.execution.TableExecutionProgress;
import io.yak.ops.plugin.task.datasync.plan.JobPlan;
import io.yak.ops.plugin.task.datasync.plan.JobPlanner;
import io.yak.ops.plugin.task.datasync.solution.SolutionType;
import io.yak.ops.common.util.JSONUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Product cardinality, resolved resources, and truthful bounded-table observations. */
class MultiTableBatchContractTest {
    private final DataSyncTaskPlugin plugin = DataSyncTaskPluginContractTest.plugin();

    @Test
    void acceptsOneOrManyExplicitRoutesWithoutChangingTheirSolutionIdentity() {
        for (int count : List.of(1, 3, 100)) {
            var parsed = plugin.parse(json(count));
            var job = JobConfig.from(parsed);
            assertEquals(SolutionType.DATABASE_OFFLINE, job.solutionType());
            assertEquals(count, job.tables().size());
            assertTrue(plugin.supportsScheduling(parsed));
            assertEquals(parsed, plugin.parse(3, ConfigParser.toJson(parsed)));
            plugin.validateReferences(parsed, new DataSyncTaskPluginContractTest.Metadata());
            plugin.validate(parsed, new DataSyncTaskPluginContractTest.Metadata());
        }
        assertThrows(TaskPluginException.class, () -> plugin.parse(json(0)));
        assertThrows(TaskPluginException.class, () -> plugin.parse(json(101)));
        assertThrows(TaskPluginException.class, () -> plugin.parse(json(2).replace("DATABASE_OFFLINE", "SINGLE_TABLE_OFFLINE")));
        assertThrows(TaskPluginException.class, () -> plugin.parse(json(2).replace("EXPLICIT", "RULES")));
    }

    @Test
    void rejectsDuplicatePhysicalAliasesAfterResolvingTheAuthorizedNamespace() {
        String duplicate = json(2).replace("orders1", "orders0");
        assertThrows(TaskPluginException.class, () -> plugin.parse(duplicate));
        String alias = json(2).replace("\"table\":\"orders1\"", "\"database\":\"app\",\"table\":\"orders0\"");
        var parsed = plugin.parse(alias);
        assertThrows(TaskPluginException.class, () -> plugin.validateReferences(parsed, new DataSyncTaskPluginContractTest.Metadata()));
        assertThrows(TaskPluginException.class, () -> plugin.parse(json(2).replace("copy1", "copy0")));
    }

    @Test
    void pinsEveryTableAndClaimBeforeCreatingAnyPhysicalConnector() {
        var job = plan(3);
        assertEquals(3, job.tables().size());
        assertEquals(3, job.admissionPlan().exclusiveResources().size());
        assertTrue(job.admissionPlan().exclusiveResources().stream().allMatch(value -> value.type().equals("DATA_SYNC_TARGET")));
        assertThrows(IllegalStateException.class, job::source);
        assertEquals(plan(3).admissionPlan(), job.admissionPlan());
        assertNotEquals(plan(2).admissionPlan().resourceRevision(), job.admissionPlan().resourceRevision());
    }

    @Test
    void sourceCompletionDoesNotPretendThatDestinationCommitHasCompleted() {
        JobPlan plan = plan(1);
        TableId table = plan.tables().getFirst().source().table();
        TableMetrics metrics = new TableMetrics();
        metrics.register(table);
        TableExecutionProgress progress = new TableExecutionProgress(plan.tables(), metrics);
        assertTrue(progress.percent().isEmpty());
        metrics.planned(table, 8);
        metrics.read(table);
        for (int index = 0; index < 3; index++) metrics.splitFinished(table);
        assertEquals(37, progress.percent().orElseThrow());
        for (int index = 3; index < 8; index++) metrics.splitFinished(table);
        var row = JSONUtils.readTree(progress.report(JobStatus.RUNNING)).path("tables").get(0);
        assertEquals("RUNNING", row.path("status").asText());
        assertEquals(99, row.path("progressPercent").asInt());
        assertEquals(0, row.path("committedRows").asInt());
        metrics.committed(table, 1);
        row = JSONUtils.readTree(progress.report(JobStatus.RUNNING)).path("tables").get(0);
        assertEquals("FINISHED", row.path("status").asText());
        assertEquals(100, row.path("progressPercent").asInt());
    }

    @Test
    void totalDenominatorWaitsForEveryTableAndFailureRetainsCommittedWork() {
        JobPlan plan = plan(2);
        TableId first = plan.tables().get(0).source().table();
        TableId second = plan.tables().get(1).source().table();
        TableMetrics metrics = new TableMetrics();
        metrics.register(first);
        metrics.register(second);
        TableExecutionProgress progress = new TableExecutionProgress(plan.tables(), metrics);
        metrics.planned(first, 1);
        metrics.read(first);
        metrics.splitFinished(first);
        assertTrue(progress.percent().isEmpty());
        metrics.committed(first, 1);
        metrics.planned(second, 7);
        assertEquals(12, progress.percent().orElseThrow());
        metrics.writeFailed(second);
        var rows = JSONUtils.readTree(progress.report(JobStatus.FAILED)).path("tables");
        assertEquals("FINISHED", rows.get(0).path("status").asText());
        assertEquals(1, rows.get(0).path("committedRows").asInt());
        assertEquals("FAILED", rows.get(1).path("status").asText());
        assertEquals("WRITE_FAILED", rows.get(1).path("failureCode").asText());
        assertEquals("SPLITS", rows.get(1).path("progressUnit").asText());
        assertFalse(rows.toString().contains("password"));
    }

    @Test
    void anEmptyTableHasARealCompletionAndUnstartedTablesCancelWithoutPercent() {
        JobPlan plan = plan(2);
        TableMetrics metrics = new TableMetrics();
        metrics.planned(plan.tables().get(0).source().table(), 0);
        var rows = JSONUtils.readTree(new TableExecutionProgress(plan.tables(), metrics).report(JobStatus.CANCELED)).path("tables");
        assertEquals("FINISHED", rows.get(0).path("status").asText());
        assertEquals(100, rows.get(0).path("progressPercent").asInt());
        assertEquals("CANCELED", rows.get(1).path("status").asText());
        assertTrue(rows.get(1).path("progressPercent").isNull());
    }

    private JobPlan plan(int count) {
        var metadata = new DataSyncTaskPluginContractTest.Metadata();
        var lookup = new DataSourceLookupService() {
            @Override
            public DataSourceTable table(String workspace, String id, String database, String schema, String name) {
                assertEquals("ws", workspace);
                DataSourceInfo info = metadata.dataSource(id);
                TableId table = info.resolveTable(database, schema, name);
                return new DataSourceTable(info.type(), table, metadata.tableSchema(id, table), 1);
            }
            @Override
            public ReadonlyConfig connectionOptions(String workspace, String id) { throw new AssertionError("Planning cannot read secrets"); }
            @Override
            public ReadonlyConfig connectionOptions(String workspace, String id, long revision) { throw new AssertionError("Planning cannot read secrets"); }
            @Override
            public DataSourceInfo dataSource(String workspace, String id) { return metadata.dataSource(id); }
        };
        return new JobPlanner(lookup, new FactoryUtil()).plan(plugin.parse(json(count)), new TaskExecutionIdentity("ws", "task", 1, "instance"));
    }

    private static String json(int count) {
        List<Map<String, Object>> tables = new ArrayList<>();
        for (int index = 0; index < count; index++) tables.add(Map.of(
                "source", Map.of("table", "orders" + index), "target", Map.of("table", "copy" + index), "writeMode", "UPSERT"));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("solutionType", "DATABASE_OFFLINE");
        values.put("steps", List.of("FULL"));
        values.put("source", Map.of("datasourceId", "source"));
        values.put("sink", Map.of("datasourceId", "target"));
        values.put("tableSelection", Map.of("mode", "EXPLICIT"));
        values.put("tableMappings", tables);
        return JSONUtils.toJson(values);
    }
}
