package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.composer.PipelineComposer;
import io.yak.ops.core.composer.PipelineContext;
import io.yak.ops.core.composer.PipelineExecution;
import io.yak.ops.core.composer.PipelinePlan;
import io.yak.ops.core.definition.PipelineDef;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** Verifies that the Task SPI only adapts and rechecks the Flink-style composer. */
class PipelineTaskPluginV5Test {
    private static final String V5 = """
            {
              "source": {"type":"JDBC", "datasourceId":"s", "syncMode":"FULL",
                         "tables":[{"table":"orders"}]},
              "sink": {"type":"JDBC", "datasourceId":"t",
                       "schemaPolicy":{"initial":"VERIFY",
                            "evolution":{"enabled":false,"allowDropColumns":false}},
                       "tables":[{"table":"orders_copy","write":{"mode":"APPEND"}}]},
              "route":[{"source-table":"orders","sink-table":"orders_copy"}],
              "pipeline":{"execution.runtime-mode":"BATCH","pipeline.table-scope":"SINGLE"}
            }
            """;

    @Test
    void executesOneAdmissionCheckPerTaskBoundaryAndDefersPhysicalStart() {
        StubComposer composer = new StubComposer();
        PipelineTaskPlugin plugin = new PipelineTaskPlugin(new FactoryUtil(), composer);
        TaskExecutionIdentity identity = new TaskExecutionIdentity("ws", "def", 1, "instance");
        ReadonlyConfig parameters = ConfigParser.parseJson(V5);

        assertEquals(5, plugin.parameterSchemaVersion());
        TaskExecutionResources admitted = plugin.plan(parameters, identity);
        assertEquals(1, composer.plans);
        TaskHandle handle = plugin.start(parameters, admitted, identity, false).toCompletableFuture().join();
        assertEquals(2, composer.plans);
        assertEquals(1, composer.compositions);
        assertEquals(0, composer.starts);
        assertFalse(handle.completion().toCompletableFuture().isDone());

        handle.start();
        assertEquals(1, composer.starts);
        assertInstanceOf(PipelineExecution.class, composer.last);
    }

    @Test
    void rejectsChangedAdmissionBeforeComposition() {
        StubComposer composer = new StubComposer();
        PipelineTaskPlugin plugin = new PipelineTaskPlugin(new FactoryUtil(), composer);
        TaskExecutionIdentity identity = new TaskExecutionIdentity("ws", "def", 1, "instance");
        ReadonlyConfig parameters = ConfigParser.parseJson(V5);
        TaskExecutionResources admitted = plugin.plan(parameters, identity);
        composer.revision = "changed";
        assertThrows(CompletionException.class,
                () -> plugin.start(parameters, admitted, identity, false).toCompletableFuture().join());
        assertEquals(0, composer.compositions);
    }

    @Test
    void cancelFlowsThroughTheTaskHandleWithoutComposerRegistry() {
        StubComposer composer = new StubComposer();
        PipelineTaskPlugin plugin = new PipelineTaskPlugin(new FactoryUtil(), composer);
        TaskExecutionIdentity identity = new TaskExecutionIdentity("ws", "def", 1, "instance");
        ReadonlyConfig parameters = ConfigParser.parseJson(V5);
        TaskExecutionResources admitted = plugin.plan(parameters, identity);
        TaskHandle handle = plugin.start(parameters, admitted, identity, false).toCompletableFuture().join();
        handle.cancel().toCompletableFuture().join();
        assertEquals(TaskOutcome.CANCELED, handle.completion().toCompletableFuture().join());
        assertEquals(0, composer.starts);
    }

    private static final class StubComposer implements PipelineComposer {
        private int plans;
        private int compositions;
        private int starts;
        private String revision = "revision";
        private PipelineExecution last;

        @Override
        public PipelinePlan plan(PipelineDef definition, PipelineContext context) {
            assertEquals("ws", context.workspaceId());
            assertEquals("JDBC", definition.getSource().getType());
            plans++;
            return new PipelinePlan(List.of(), revision);
        }

        @Override
        public PipelineExecution compose(PipelineDef definition, PipelinePlan plan, PipelineContext context) {
            assertEquals(revision, plan.resourceRevision());
            compositions++;
            last = new StubExecution(this, context.instanceId());
            return last;
        }
    }

    private static final class StubExecution implements PipelineExecution {
        private final StubComposer composer;
        private final String id;
        private final CompletableFuture<JobStatus> terminal = new CompletableFuture<>();
        private JobStatus status = JobStatus.CREATED;

        private StubExecution(StubComposer composer, String id) {
            this.composer = composer;
            this.id = id;
        }

        @Override
        public String getJobId() {
            return id;
        }

        @Override
        public void start() {
            if (status != JobStatus.CREATED) return;
            status = JobStatus.RUNNING;
            composer.starts++;
        }

        @Override
        public JobStatus getStatus() {
            return status;
        }

        @Override
        public CompletionStage<JobStatus> completion() {
            return terminal;
        }

        @Override
        public CompletionStage<Void> cancel() {
            status = JobStatus.CANCELED;
            terminal.complete(status);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public OptionalInt getProgressPercent() {
            return OptionalInt.empty();
        }

        @Override
        public Optional<String> getExecutionReport() {
            return Optional.empty();
        }

        @Override
        public Optional<String> getExecutionSummary() {
            return Optional.empty();
        }

        @Override
        public CompletionStage<Optional<String>> engineJobIdReady() {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public Optional<String> engineJobId() {
            return Optional.empty();
        }
    }
}
