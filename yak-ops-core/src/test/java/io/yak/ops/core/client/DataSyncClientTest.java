package io.yak.ops.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.definition.PipelineDef;
import io.yak.ops.core.parser.JsonPipelineDefinitionParser;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class DataSyncClientTest {
    private static final String V4 = "{\"solutionType\":\"SINGLE_TABLE_OFFLINE\"}";
    private static final DataSyncContext CONTEXT =
            new DataSyncContext("ws", "definition", 1, "instance", false);

    @Test
    void submitStartsImmediatelyWhilePrepareRemainsDeferred() {
        StubEngine engine = new StubEngine();
        DataSyncClient client = new DataSyncClient(new JsonPipelineDefinitionParser(), engine);
        DataSyncJob job = client.prepare(V4, CONTEXT);
        assertEquals(JobStatus.CREATED, job.getStatus());
        assertEquals(0, engine.starts);
        assertSame(job, client.prepare(V4, CONTEXT));
        assertEquals(1, engine.preparations);
        client.submit(V4, CONTEXT);
        assertEquals(1, engine.starts);
        assertEquals(JobStatus.RUNNING, job.getStatus());
    }

    @Test
    void duplicateIdentityWithDifferentDefinitionIsRejected() {
        DataSyncClient client = new DataSyncClient(new JsonPipelineDefinitionParser(), new StubEngine());
        client.prepare(V4, CONTEXT);
        assertThrows(IllegalArgumentException.class,
                () -> client.prepare("{\"solutionType\":\"SINGLE_TABLE_REALTIME\"}", CONTEXT));
    }

    @Test
    void cancellationIsWorkspaceScopedAndCompletesJob() {
        StubEngine engine = new StubEngine();
        DataSyncClient client = new DataSyncClient(new JsonPipelineDefinitionParser(), engine);
        DataSyncJob job = client.submit(V4, CONTEXT);
        DataSyncContext other =
                new DataSyncContext("other", "definition", 1, "instance", false);
        assertFalse(client.getJob("instance", other).isPresent());
        assertThrows(Exception.class, () -> client.cancel("instance", other).toCompletableFuture().join());
        client.cancel("instance", CONTEXT).toCompletableFuture().join();
        assertEquals(JobStatus.CANCELED, job.completion().toCompletableFuture().join());
        assertTrue(client.getJob("instance", CONTEXT).isPresent());
    }

    private static final class StubEngine implements DataSyncEngine {
        private int preparations;
        private int starts;

        @Override
        public void validate(PipelineDef definition, DataSyncContext context) {}

        @Override
        public DataSyncPlan plan(PipelineDef definition, DataSyncContext context) {
            return new DataSyncPlan(List.of(), "revision");
        }

        @Override
        public DataSyncJob prepare(PipelineDef definition, DataSyncPlan plan, DataSyncContext context) {
            preparations++;
            return new StubJob(context.instanceId(), this);
        }
    }

    private static final class StubJob implements DataSyncJob {
        private final String id;
        private final StubEngine engine;
        private final CompletableFuture<JobStatus> result = new CompletableFuture<>();
        private JobStatus status = JobStatus.CREATED;

        private StubJob(String id, StubEngine engine) {
            this.id = id;
            this.engine = engine;
        }

        @Override
        public String getJobId() { return id; }

        @Override
        public void start() {
            if (status == JobStatus.CREATED) {
                status = JobStatus.RUNNING;
                engine.starts++;
            }
        }

        @Override
        public JobStatus getStatus() { return status; }

        @Override
        public CompletionStage<JobStatus> completion() { return result; }

        @Override
        public CompletionStage<Void> cancel() {
            status = JobStatus.CANCELED;
            result.complete(status);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public OptionalInt getProgressPercent() { return OptionalInt.empty(); }

        @Override
        public Optional<String> getExecutionReport() { return Optional.empty(); }

        @Override
        public Optional<String> getExecutionSummary() { return Optional.empty(); }

        @Override
        public CompletionStage<Optional<String>> engineJobIdReady() {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public Optional<String> engineJobId() { return Optional.empty(); }
    }
}
