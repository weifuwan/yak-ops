package io.yak.ops.composer.yakflow;

import io.yak.ops.composer.PipelineContext;
import io.yak.ops.composer.PipelineExecution;
import io.yak.ops.composer.PipelinePlan;
import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.composer.definition.PipelineOptions;
import io.yak.ops.composer.execution.ExecutionPhase;
import io.yak.ops.composer.execution.IncrementalBatchPreparation;
import io.yak.ops.composer.execution.SchemaPreparation;
import io.yak.ops.composer.execution.TableExecutionProgress;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.exceptions.SchemaEvolveException;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Owns every DATA_SYNC execution from platform registration through physical termination. */
public final class YakFlowPipelineExecution implements PipelineExecution {
    private static final Logger LOG = LoggerFactory.getLogger(YakFlowPipelineExecution.class);
    private final PipelineDef definition;
    private final PipelinePlan admission;
    private final PipelineContext context;
    private final TaskExecutionIdentity identity;
    private final boolean debug;
    private final YakFlowPipelineComposer composer;
    private final PipelineExecutor executor;
    private final SchemaPreparation preparation;
    private final IncrementalBatchPreparation incrementalPreparation;
    private final CompletableFuture<JobStatus> result = new CompletableFuture<>();
    private final CompletableFuture<Optional<String>> reference = new CompletableFuture<>();
    private volatile TableExecutionProgress progress;
    private volatile JobClient runtime;
    private volatile JobStatus status = JobStatus.CREATED;
    private boolean started;
    private volatile boolean canceled;
    private boolean submissionInvoked;
    private CompletableFuture<Void> cancellation;

    public YakFlowPipelineExecution(
            PipelineDef definition,
            PipelinePlan admission,
            PipelineContext context,
            YakFlowPipelineComposer composer,
            PipelineExecutor executor,
            IncrementalBatchPreparation incrementalPreparation) {
        this.definition = definition;
        this.admission = admission;
        this.context = context;
        this.identity = new TaskExecutionIdentity(
                context.workspaceId(), context.definitionId(), context.definitionVersion(), context.instanceId());
        this.debug = context.debug();
        this.composer = composer;
        this.executor = executor;
        this.incrementalPreparation = incrementalPreparation;
        this.preparation = new SchemaPreparation(
                table -> composer.createMetadataApplier(table, identity),
                table -> composer.preparedTarget(table, identity));
    }

    @Override
    public String getJobId() {
        return identity.instanceId();
    }

    @Override
    public JobStatus getStatus() {
        return status;
    }

    @Override
    public synchronized void start() {
        if (started || result.isDone()) return;
        started = true;
        Thread worker =
                Thread.ofVirtual().name("data-sync-" + identity.instanceId()).unstarted(this::execute);
        worker.start();
    }

    private void execute() {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("taskLogId", identity.instanceId())) {
            try {
                checkCanceled();
                PipelinePlan plan = composer.plan(definition, context);
                verifyAdmission(plan);
                PipelineOptions config = PipelineOptions.from(definition);
                IncrementalBatchPreparation.IncrementalRun incremental =
                        config.bounded() && config.steps().contains(ExecutionPhase.INCREMENTAL)
                                ? incrementalPreparation.prepare(plan, config, identity)
                                : null;
                boolean continuous = !config.bounded() && config.steps().contains(ExecutionPhase.INCREMENTAL);
                String dataStep = continuous
                        ? (config.steps().contains(ExecutionPhase.FULL) ? "FULL" : "INCREMENTAL")
                        : (incremental == null || incremental.initialLoad() ? "FULL" : "INCREMENTAL");
                progress = new TableExecutionProgress(plan.tables(), new TableMetrics(), continuous, dataStep);
                checkCanceled();
                io.yak.ops.configuration.Configuration configuration =
                        composer.configuration(definition, identity, debug);
                io.yak.ops.flow.runtime.transformations.SourceTransformation<io.yak.ops.core.event.Event> source =
                        composer.translateSource(
                                definition,
                                incremental == null ? plan : incremental.plan(),
                                identity,
                                progress,
                                configuration);
                checkCanceled();
                status = JobStatus.RUNNING;
                if (config.steps().contains(ExecutionPhase.SCHEMA)) {
                    LOG.info(
                            "Schema preparation started, tableCount={}",
                            plan.tables().size());
                    preparation.prepare(plan, identity, progress);
                }
                checkCanceled();
                // Schema preparation and cursor freezing change the physical plan. Revalidate
                // its resource binding before submission; otherwise reuse the just-admitted plan.
                PipelinePlan current = config.steps().contains(ExecutionPhase.SCHEMA) || incremental != null
                        ? composer.plan(definition, context)
                        : plan;
                verifyAdmission(current);
                io.yak.ops.flow.runtime.graph.StreamGraph graph = composer.translate(
                        definition,
                        incremental == null ? current : incremental.apply(current),
                        identity,
                        progress,
                        configuration,
                        source);
                synchronized (this) {
                    checkCanceled();
                    submissionInvoked = true;
                    progress.dataSubmissionStarted();
                }
                io.yak.ops.core.execution.JobClient client =
                        executor.execute(graph, configuration).join();
                runtime = client;
                reference.complete(Optional.of(client.getJobID().toString()));
                if (canceled) runtime.cancel();
                status = terminalStatus(client).toCompletableFuture().join();
                result.complete(status);
            } catch (Throwable failure) {
                if (submissionInvoked
                        || (failure instanceof SchemaEvolveException schema && schema.isOutcomeUnknown())) {
                    // A failed submission future cannot prove that the engine never accepted the Job.
                    status = JobStatus.FAILING;
                    result.completeExceptionally(failure);
                } else {
                    status = canceled ? JobStatus.CANCELED : JobStatus.FAILED;
                    if (progress != null) progress.preparationEnded();
                    LOG.warn(
                            "Data Sync preparation ended before Job submission, instanceId={}, exceptionType={}",
                            identity.instanceId(),
                            failure.getClass().getSimpleName());
                    result.complete(status);
                }
            } finally {
                reference.complete(Optional.empty());
            }
        }
    }

    /** Returns authoritative engine status even when the physical result future is exceptional. */
    static CompletionStage<JobStatus> terminalStatus(JobClient client) {
        return client.getJobExecutionResult()
                .handle((result, failure) -> failure)
                .thenCompose(resultFailure -> client.getJobStatus().handle((jobStatus, statusFailure) -> {
                    if (statusFailure != null) {
                        if (resultFailure != null && statusFailure != resultFailure) {
                            statusFailure.addSuppressed(resultFailure);
                        }
                        throw new CompletionException(statusFailure);
                    }
                    if (jobStatus != JobStatus.FINISHED
                            && jobStatus != JobStatus.CANCELED
                            && jobStatus != JobStatus.FAILED) {
                        throw new IllegalStateException("YakFlow returned a result before terminal Job state");
                    }
                    return jobStatus;
                }));
    }

    private void verifyAdmission(PipelinePlan plan) {
        if (!admission.sameAdmission(plan)) {
            throw new IllegalStateException("DATA_SYNC resource binding changed before execution");
        }
    }

    private void checkCanceled() {
        if (canceled || Thread.currentThread().isInterrupted())
            throw new CancellationException("Data Sync was canceled");
    }

    @Override
    public Optional<String> engineJobId() {
        return reference.getNow(Optional.empty());
    }

    @Override
    public CompletionStage<Optional<String>> engineJobIdReady() {
        return reference;
    }

    @Override
    public CompletionStage<JobStatus> completion() {
        return result;
    }

    @Override
    public OptionalInt getProgressPercent() {
        TableExecutionProgress current = progress;
        return current == null ? OptionalInt.empty() : current.percent();
    }

    @Override
    public Optional<String> getExecutionReport() {
        TableExecutionProgress current = progress;
        return current == null ? Optional.empty() : Optional.of(current.report(status));
    }

    @Override
    public Optional<String> getExecutionSummary() {
        TableExecutionProgress current = progress;
        return current == null ? Optional.empty() : Optional.ofNullable(current.summary(status));
    }

    @Override
    public synchronized CompletionStage<Void> cancel() {
        if (cancellation != null) return cancellation;
        canceled = true;
        cancellation = new CompletableFuture<>();
        if (!started) {
            status = JobStatus.CANCELED;
            result.complete(JobStatus.CANCELED);
            reference.complete(Optional.empty());
            cancellation.complete(null);
            return cancellation;
        }
        // JDBC cancellation must use the driver: interrupting a virtual thread can close its
        // socket before a server response confirms that DDL has stopped.
        preparation.requestCancellation();
        Thread.ofVirtual().name("data-sync-cancel-" + identity.instanceId()).start(() -> {
            try (MDC.MDCCloseable ignored = MDC.putCloseable("taskLogId", identity.instanceId())) {
                try {
                    preparation.cancel();
                    JobClient running = runtime;
                    if (running == null) cancellation.complete(null);
                    else
                        running.cancel().whenComplete((unused, failure) -> {
                            if (failure == null) cancellation.complete(null);
                            else cancellation.completeExceptionally(failure);
                        });
                } catch (Throwable failure) {
                    cancellation.completeExceptionally(failure);
                }
            }
        });
        return cancellation;
    }
}
