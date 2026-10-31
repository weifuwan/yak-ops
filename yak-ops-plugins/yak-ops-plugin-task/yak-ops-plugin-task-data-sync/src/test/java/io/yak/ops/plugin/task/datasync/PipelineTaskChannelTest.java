package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.composer.PipelineComposer;
import io.yak.ops.composer.PipelineContext;
import io.yak.ops.composer.PipelineExecution;
import io.yak.ops.composer.PipelinePlan;
import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskStartRejectedException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/** Task admission and execution stay deferred across the Connector-based definition boundary. */
class PipelineTaskChannelTest {
    private static final String JSON = """
            {"source":{"type":"mysql","datasourceId":"source","startup":{"mode":"LATEST_OFFSET"},
                       "tables":[{"database":"app","table":"orders"}]},
             "sink":{"type":"jdbc-mysql","datasourceId":"target",
                     "schemaPolicy":{"initial":"VERIFY","evolution":{"enabled":false,"allowDropColumns":false}},
                     "tables":[{"database":"ods","table":"orders","write":{"mode":"UPSERT"}}]},
             "route":[{"source-table":"app.orders","sink-table":"ods.orders"}],"transform":[],
             "pipeline":{"execution.runtime-mode":"STREAMING","pipeline.table-scope":"SINGLE"},
             "index":{"sourceType":"forged","runtimeMode":"BATCH"}}
            """;
    private static final TaskExecutionIdentity IDENTITY = new TaskExecutionIdentity("workspace", "definition", 3, "instance");

    @Test
    void recomputesSearchMetadataAndRejectsOldVersionsWithoutResourceAccess() {
        PipelineTaskChannel channel = channel(new TestComposer());
        ReadonlyConfig parsed = channel.parse(JSON);
        Map<?, ?> index = (Map<?, ?>) parsed.getSourceMap().get("index");
        assertEquals("mysql", index.get("sourceType"));
        assertEquals("jdbc-mysql", index.get("sinkType"));
        assertEquals("STREAMING", index.get("runtimeMode"));
        assertFalse(index.containsKey("solutionType"));
        assertFalse(channel.supportsScheduling(parsed));
        assertThrows(TaskPluginException.class, () -> channel.parse(6, JSON));
    }

    @Test
    void readsPublishedSevenAndWritesEightWithoutInventingHistoricalOptions() {
        PipelineTaskChannel channel = channel(new TestComposer());
        assertEquals(8, channel.parameterSchemaVersion());
        assertEquals(channel.parse(7, JSON).getSourceMap(), channel.parse(8, JSON).getSourceMap());
        assertThrows(TaskPluginException.class, () -> channel.parse(9, JSON));
        String optionalSnapshot = JSON.replace("\"table\":\"orders\"}]", "\"table\":\"orders\",\"snapshot\":{}}]");
        assertEquals(channel.parse(7, optionalSnapshot).getSourceMap(), channel.parse(8, optionalSnapshot).getSourceMap());
    }

    @Test
    void rejectsNewTableOptionsWhenLabeledAsPublishedSchemaSeven() {
        PipelineTaskChannel channel = channel(new TestComposer());
        String dml = JSON.replace("\"table\":\"orders\"}]",
                "\"table\":\"orders\",\"dml\":{\"events\":[\"INSERT\",\"UPDATE\"]}}]");
        assertThrows(TaskPluginException.class, () -> channel.parse(7, dml));
        assertTrue(channel.parse(8, dml).getSourceMap().containsKey("source"));
    }

    @Test
    void acceptsBoundedJdbcReadAndWriteOptionsUnderSchemaEight() {
        PipelineTaskChannel channel = channel(new TestComposer());
        String batch = JSON.replace("\"type\":\"mysql\"", "\"type\":\"jdbc-mysql\"")
                .replace("\"startup\":{\"mode\":\"LATEST_OFFSET\"}", "\"syncMode\":\"FULL\"")
                .replace("\"table\":\"orders\"}]", """
                        "table":"orders","read":{"where":"id > 5","splitKey":"id"},
                        "options":{"fetchSize":250,"queryTimeoutSeconds":15}}]
                        """)
                .replace("\"write\":{\"mode\":\"UPSERT\"}", """
                        "write":{"mode":"REPLACE","batchSize":100,
                                 "preSql":["DELETE FROM ods.orders WHERE id > 5"],"postSql":[]}
                        """)
                .replace("STREAMING", "BATCH");
        assertTrue(channel.supportsScheduling(channel.parse(8, batch)));
        assertThrows(TaskPluginException.class, () -> channel.parse(7, batch));
    }

    @Test
    void schedulesBoundedCdcSnapshotsUsingTheirRuntimeMode() {
        PipelineTaskChannel channel = channel(new TestComposer());
        String snapshot = JSON.replace("LATEST_OFFSET", "SNAPSHOT").replace("STREAMING", "BATCH");
        assertTrue(channel.supportsScheduling(channel.parse(snapshot)));
    }

    @Test
    void defersPhysicalStartUntilTaskRegistrationAndForwardsCancellation() {
        TestComposer composer = new TestComposer();
        PipelineTaskChannel channel = channel(composer);
        ReadonlyConfig parameters = channel.parse(JSON);
        TaskExecutionResources admitted = channel.plan(parameters, IDENTITY);
        TaskHandle task = channel.createTask(parameters, admitted, IDENTITY, false).toCompletableFuture().join();
        assertFalse(composer.execution.started);
        task.start();
        assertTrue(composer.execution.started);
        task.cancel().toCompletableFuture().join();
        assertEquals(JobStatus.CANCELED, composer.execution.getStatus());
    }

    @Test
    void rejectsChangedAdmissionRevisionBeforeComposition() {
        TestComposer composer = new TestComposer();
        PipelineTaskChannel channel = channel(composer);
        ReadonlyConfig parameters = channel.parse(JSON);
        TaskExecutionResources admitted = channel.plan(parameters, IDENTITY);
        composer.revision = "changed-revision";
        CompletionException failure = assertThrows(CompletionException.class,
                () -> channel.createTask(parameters, admitted, IDENTITY, false).toCompletableFuture().join());
        assertInstanceOf(TaskStartRejectedException.class, failure.getCause());
        assertFalse(composer.composed);
        assertFalse(composer.execution.started);
    }

    private static PipelineTaskChannel channel(TestComposer composer) {
        return new PipelineTaskChannel(new FactoryUtil(), composer);
    }

    private static final class TestComposer implements PipelineComposer {
        private String revision = "revision";
        private boolean composed;
        private final TestExecution execution = new TestExecution();

        @Override
        public PipelinePlan plan(PipelineDef definition, PipelineContext context) {
            assertEquals("workspace", context.workspaceId());
            return new PipelinePlan(List.of(new PipelinePlan.ResourceClaim("TABLE", "target")), revision);
        }

        @Override
        public PipelineExecution compose(PipelineDef definition, PipelinePlan plan, PipelineContext context) {
            composed = true;
            return execution;
        }
    }

    private static final class TestExecution implements PipelineExecution {
        private boolean started;
        private JobStatus status = JobStatus.CREATED;
        private final CompletableFuture<JobStatus> completion = new CompletableFuture<>();

        @Override
        public String getJobId() { return "job"; }

        @Override
        public void start() { started = true; status = JobStatus.RUNNING; }

        @Override
        public JobStatus getStatus() { return status; }

        @Override
        public CompletionStage<JobStatus> completion() { return completion; }

        @Override
        public CompletionStage<Void> cancel() {
            status = JobStatus.CANCELED;
            completion.complete(status);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public OptionalInt getProgressPercent() { return OptionalInt.empty(); }

        @Override
        public Optional<String> getExecutionReport() { return Optional.empty(); }

        @Override
        public Optional<String> getExecutionSummary() { return Optional.empty(); }

        @Override
        public CompletionStage<Optional<String>> engineJobIdReady() { return CompletableFuture.completedFuture(engineJobId()); }

        @Override
        public Optional<String> engineJobId() { return Optional.of("job"); }
    }
}
