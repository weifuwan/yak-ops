package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.exceptions.SchemaEvolveException;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.plugin.task.api.execution.TaskRuntimeReference;
import io.yak.ops.plugin.task.datasync.config.JobConfig;
import io.yak.ops.plugin.task.datasync.pipeline.PipelineBuilder;
import io.yak.ops.plugin.task.datasync.plan.JobPlan;
import io.yak.ops.plugin.task.datasync.plan.JobPlanner;
import io.yak.ops.plugin.task.datasync.solution.Step;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Owns every DATA_SYNC execution from platform registration through physical termination. */
final class DataSyncTaskHandle implements TaskHandle {
    private static final Logger LOG = LoggerFactory.getLogger(DataSyncTaskHandle.class);
    private final ReadonlyConfig parameters;
    private final TaskExecutionResources admission;
    private final TaskExecutionIdentity identity;
    private final boolean debug;
    private final JobPlanner planner;
    private final PipelineBuilder builder;
    private final PipelineExecutor executor;
    private final SchemaPreparation preparation;
    private final IncrementalBatchPreparation incrementalPreparation;
    private final CompletableFuture<TaskOutcome> result = new CompletableFuture<>();
    private final CompletableFuture<Optional<TaskRuntimeReference>> reference = new CompletableFuture<>();
    private volatile TableExecutionProgress progress;
    private volatile YakFlowTaskHandle runtime;
    private volatile JobStatus status = JobStatus.CREATED;
    private boolean started;
    private volatile boolean canceled;
    private boolean submissionInvoked;
    private CompletableFuture<Void> cancellation;

    DataSyncTaskHandle(
            ReadonlyConfig parameters,
            TaskExecutionResources admission,
            TaskExecutionIdentity identity,
            boolean debug,
            JobPlanner planner,
            PipelineBuilder builder,
            PipelineExecutor executor,
            IncrementalBatchPreparation incrementalPreparation) {
        this.incrementalPreparation = incrementalPreparation;
        this.parameters = parameters;
        this.admission = admission;
        this.identity = identity;
        this.debug = debug;
        this.planner = planner;
        this.builder = builder;
        this.executor = executor;
        this.preparation = new SchemaPreparation(builder, planner);
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
                JobPlan plan = planner.plan(parameters, identity);
                verifyAdmission(plan);
                JobConfig config = JobConfig.from(parameters);
                IncrementalBatchPreparation.IncrementalRun incremental =
                        config.solutionType().supportsScheduling()
                                        && config.steps().contains(Step.INCREMENTAL)
                                ? incrementalPreparation.prepare(plan, config, identity)
                                : null;
                boolean continuous = !config.solutionType().supportsScheduling()
                        && config.steps().contains(Step.INCREMENTAL);
                String dataStep = continuous
                        ? (config.steps().contains(Step.FULL) ? "FULL" : "INCREMENTAL")
                        : (incremental == null || incremental.initialLoad() ? "FULL" : "INCREMENTAL");
                progress = new TableExecutionProgress(plan.tables(), new TableMetrics(), continuous, dataStep);
                status = JobStatus.RUNNING;
                if (config.steps().contains(Step.SCHEMA)) {
                    LOG.info(
                            "Schema preparation started, tableCount={}",
                            plan.tables().size());
                    preparation.prepare(plan, identity, progress);
                }
                checkCanceled();
                // Schema preparation and cursor freezing change the physical plan. Revalidate
                // its resource binding before submission; otherwise reuse the just-admitted plan.
                JobPlan current = config.steps().contains(Step.SCHEMA) || incremental != null
                        ? planner.plan(parameters, identity)
                        : plan;
                verifyAdmission(current);
                io.yak.ops.plugin.task.datasync.pipeline.CompiledPipeline pipeline = builder.build(
                        incremental == null ? current : incremental.apply(current),
                        identity,
                        debug,
                        progress,
                        config.schemaChangesEnabled(),
                        config.allowDropColumns());
                synchronized (this) {
                    checkCanceled();
                    submissionInvoked = true;
                    progress.dataSubmissionStarted();
                }
                io.yak.ops.core.execution.JobClient client = executor.execute(
                                pipeline.graph(), pipeline.configuration())
                        .join();
                runtime = new YakFlowTaskHandle(client, progress);
                reference.complete(runtime.runtimeReference());
                if (canceled) runtime.cancel();
                TaskOutcome outcome = runtime.completion().toCompletableFuture().join();
                status = switch (outcome) {
                    case FINISHED -> JobStatus.FINISHED;
                    case FAILED -> JobStatus.FAILED;
                    case CANCELED -> JobStatus.CANCELED;
                };
                result.complete(outcome);
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
                    result.complete(canceled ? TaskOutcome.CANCELED : TaskOutcome.FAILED);
                }
            } finally {
                reference.complete(Optional.empty());
            }
        }
    }

    private void verifyAdmission(JobPlan plan) {
        if (!admission.equals(plan.admissionPlan())) {
            throw new IllegalStateException("DATA_SYNC resource binding changed before execution");
        }
    }

    private void checkCanceled() {
        if (canceled || Thread.currentThread().isInterrupted())
            throw new CancellationException("Data Sync was canceled");
    }

    @Override
    public Optional<TaskRuntimeReference> runtimeReference() {
        return reference.getNow(Optional.empty());
    }

    @Override
    public CompletionStage<Optional<TaskRuntimeReference>> runtimeReferenceReady() {
        return reference;
    }

    @Override
    public CompletionStage<TaskOutcome> completion() {
        return result;
    }

    @Override
    public OptionalInt progressPercent() {
        TableExecutionProgress current = progress;
        return current == null ? OptionalInt.empty() : current.percent();
    }

    @Override
    public Optional<String> executionReport() {
        TableExecutionProgress current = progress;
        YakFlowTaskHandle running = runtime;
        return running != null
                ? running.executionReport()
                : current == null ? Optional.empty() : Optional.of(current.report(status));
    }

    @Override
    public Optional<String> executionSummary() {
        TableExecutionProgress current = progress;
        YakFlowTaskHandle running = runtime;
        return running != null
                ? running.executionSummary()
                : current == null ? Optional.empty() : Optional.ofNullable(current.summary(status));
    }

    @Override
    public synchronized CompletionStage<Void> cancel() {
        if (cancellation != null) return cancellation;
        canceled = true;
        cancellation = new CompletableFuture<>();
        if (!started) {
            status = JobStatus.CANCELED;
            result.complete(TaskOutcome.CANCELED);
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
                    YakFlowTaskHandle running = runtime;
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
