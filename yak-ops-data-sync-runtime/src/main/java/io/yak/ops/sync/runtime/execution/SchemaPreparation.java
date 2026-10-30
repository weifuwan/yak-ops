package io.yak.ops.sync.runtime.execution;

import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.event.SchemaChangeEventType;
import io.yak.ops.core.exceptions.SchemaEvolveException;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.sync.runtime.pipeline.PipelineBuilder;
import io.yak.ops.sync.runtime.plan.JobPlan;
import io.yak.ops.sync.runtime.plan.JobPlanner;
import io.yak.ops.sync.runtime.plan.TablePlan;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;

/** Owns one Task's ordered target preparation and driver-level cancellation requests. */
public final class SchemaPreparation {
    private final PipelineBuilder builder;
    private final JobPlanner planner;
    private boolean canceled;
    private MetadataApplier active;

    public SchemaPreparation(PipelineBuilder builder, JobPlanner planner) {
        this.builder = java.util.Objects.requireNonNull(builder, "builder");
        this.planner = java.util.Objects.requireNonNull(planner, "planner");
    }

    /**
     * Creates or verifies all admitted targets before any data pipeline is submitted.
     *
     * <p>The execution owner re-plans after DDL to verify the complete admission binding.
     *
     * @param plan complete admitted table set
     * @param identity trusted Task identity
     * @param progress Task-owned table observations
     * @throws Exception if any operation, readback, or resource close fails
     */
    public void prepare(JobPlan plan, TaskExecutionIdentity identity, TableExecutionProgress progress)
            throws Exception {
        for (int index = 0; index < plan.tables().size(); index++) {
            TablePlan table = plan.tables().get(index);
            checkCanceled();
            progress.schemaStarted(index);
            try {
                java.util.List<io.yak.ops.core.event.SchemaChangeEvent> changes = table.schemaChanges();
                if (!changes.isEmpty()) {
                    try (MetadataApplier applier = builder.createMetadataApplier(table, identity)) {
                        synchronized (this) {
                            checkCanceled();
                            active = applier;
                        }
                        Set<SchemaChangeEventType> accepted = new HashSet<>();
                        for (io.yak.ops.core.event.SchemaChangeEvent change : changes) accepted.add(change.getType());
                        if (!accepted.equals(Set.of(SchemaChangeEventType.CREATE_TABLE))) {
                            applier.setAcceptedSchemaEvolutionTypes(Set.copyOf(accepted));
                        }
                        for (io.yak.ops.core.event.SchemaChangeEvent change : changes) {
                            checkCanceled();
                            applier.applySchemaChange(change);
                        }
                    } finally {
                        synchronized (this) {
                            active = null;
                        }
                    }
                }
                checkCanceled();
                planner.preparedTarget(table, identity);
                checkCanceled();
                progress.schemaFinished(index, changes.isEmpty() ? "VERIFIED" : "PREPARED");
            } catch (Exception failure) {
                if (failure instanceof SchemaEvolveException schema && schema.isOutcomeUnknown()) {
                    progress.schemaUnknown(index);
                } else if (isCanceled()) progress.schemaCanceled(index);
                else progress.schemaFailed(index, "SCHEMA_PREPARATION_FAILED");
                throw failure;
            }
        }
    }

    /** Records cancellation synchronously before an asynchronous driver request is dispatched. */
    public synchronized void requestCancellation() {
        canceled = true;
    }

    /**
     * Records cancellation and requests driver cancellation without releasing target ownership.
     *
     * @throws Exception if the active driver rejects the request
     */
    public void cancel() throws Exception {
        MetadataApplier applying;
        synchronized (this) {
            canceled = true;
            applying = active;
        }
        if (applying != null) applying.cancel();
    }

    private synchronized boolean isCanceled() {
        return canceled || Thread.currentThread().isInterrupted();
    }

    private void checkCanceled() {
        if (isCanceled()) throw new CancellationException("Schema preparation was canceled");
    }
}
