package io.yak.ops.core.client;

import io.yak.ops.core.definition.PipelineDef;
import io.yak.ops.core.parser.PipelineDefinitionParser;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Unified local submission entry point for V5 or pinned V4 pipeline JSON.
 * Preparation is deliberately separate from start so Task Instance registration
 * and persistence can precede physical execution.
 */
public final class DataSyncClient {
    private static final int MAX_TRACKED_JOBS = 2048;

    private final PipelineDefinitionParser parser;
    private final DataSyncEngine engine;
    private final ConcurrentMap<String, RegisteredJob> jobs = new ConcurrentHashMap<>();

    public DataSyncClient(PipelineDefinitionParser parser, DataSyncEngine engine) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public PipelineDef parse(String json) {
        return parser.parse(json);
    }

    public void validate(String json, DataSyncContext context) {
        engine.validate(parse(json), Objects.requireNonNull(context, "context"));
    }

    public DataSyncPlan plan(String json, DataSyncContext context) {
        PipelineDef definition = parse(json);
        engine.validate(definition, Objects.requireNonNull(context, "context"));
        return engine.plan(definition, context);
    }

    /**
     * Accepts a job and immediately starts it. For Task scheduling, call prepare()
     * and start the returned job only after durable Task Instance registration.
     */
    public DataSyncJob submit(String json, DataSyncContext context) {
        DataSyncJob job = prepare(json, context);
        job.start();
        return job;
    }

    public DataSyncJob prepare(String json, DataSyncContext context) {
        Objects.requireNonNull(context, "context");
        PipelineDef definition = parse(json);
        engine.validate(definition, context);
        String key = key(context.workspaceId(), context.instanceId());
        RegisteredJob registered = jobs.compute(key, (ignored, previous) -> {
            if (previous != null) {
                if (!previous.definition.getOriginal().getSourceMap()
                        .equals(definition.getOriginal().getSourceMap())
                        || !previous.definitionId.equals(context.definitionId())
                        || previous.definitionVersion != context.definitionVersion()) {
                    throw new IllegalArgumentException("Conflicting Data Sync submission identity");
                }
                return previous;
            }
            if (jobs.size() >= MAX_TRACKED_JOBS) {
                jobs.entrySet().removeIf(entry ->
                        entry.getValue().job.completion().toCompletableFuture().isDone());
                if (jobs.size() >= MAX_TRACKED_JOBS) {
                    throw new IllegalStateException("Too many tracked Data Sync jobs");
                }
            }
            DataSyncPlan plan = engine.plan(definition, context);
            DataSyncJob job = Objects.requireNonNull(
                    engine.prepare(definition, plan, context), "engine.prepare returned null");
            if (!context.instanceId().equals(job.getJobId())) {
                throw new IllegalStateException("Data Sync job identity differs from trusted Instance");
            }
            return new RegisteredJob(definition, context.definitionId(), context.definitionVersion(), job);
        });
        return registered.job;
    }

    /** Only jobs owned by this process are observable; this is not durable recovery. */
    public Optional<DataSyncJob> getJob(String jobId, DataSyncContext context) {
        Objects.requireNonNull(context, "context");
        RegisteredJob job = jobs.get(key(context.workspaceId(), jobId));
        return job == null ? Optional.empty() : Optional.of(job.job);
    }

    public CompletionStage<Void> cancel(String jobId, DataSyncContext context) {
        return getJob(jobId, context)
                .map(DataSyncJob::cancel)
                .orElseGet(() -> CompletableFuture.failedFuture(
                        new IllegalArgumentException("Data Sync job is not owned by this process")));
    }

    private static String key(String workspaceId, String jobId) {
        return workspaceId.length() + ":" + workspaceId + ":" + Objects.requireNonNull(jobId, "jobId");
    }

    private static final class RegisteredJob {
        private final PipelineDef definition;
        private final String definitionId;
        private final int definitionVersion;
        private final DataSyncJob job;

        private RegisteredJob(PipelineDef definition, String definitionId, int definitionVersion, DataSyncJob job) {
            this.definition = definition;
            this.definitionId = definitionId;
            this.definitionVersion = definitionVersion;
            this.job = job;
        }
    }
}
