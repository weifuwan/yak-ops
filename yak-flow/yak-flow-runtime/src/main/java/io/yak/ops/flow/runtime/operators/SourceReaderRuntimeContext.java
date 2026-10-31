package io.yak.ops.flow.runtime.operators;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.SchemaChangeRequest;
import io.yak.ops.core.event.SourceSchemaChange;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.operators.coordination.OperatorEventGateway;
import io.yak.ops.flow.runtime.schema.SchemaCoordinator;
import io.yak.ops.flow.runtime.source.event.RequestSplitEvent;
import io.yak.ops.flow.runtime.source.event.SourceEventWrapper;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Runtime context passed to one SourceReader.
 *
 * <p>Identity and configuration come from the owning TaskEnvironment. Split requests and
 * Source events are sent asynchronously to the coordinator; the task mailbox must not wait
 * for the coordinator acknowledgment.
 */
public final class SourceReaderRuntimeContext implements SourceReaderContext {

    private final TaskEnvironment environment;
    private final OperatorEventGateway eventGateway;
    private final Consumer<Throwable> asyncFailureHandler;
    private final IntConsumer completedSplits;
    private final SchemaCoordinator schemaCoordinator;

    public SourceReaderRuntimeContext(
            TaskEnvironment environment, OperatorEventGateway eventGateway, Consumer<Throwable> asyncFailureHandler) {
        this(environment, eventGateway, asyncFailureHandler, ignored -> {}, null);
    }

    /** Attaches the bounded split progress collector owned by the physical Job. */
    public SourceReaderRuntimeContext(
            TaskEnvironment environment,
            OperatorEventGateway eventGateway,
            Consumer<Throwable> asyncFailureHandler,
            IntConsumer completedSplits) {
        this(environment, eventGateway, asyncFailureHandler, completedSplits, null);
    }

    /** Adds the optional runtime schema coordinator without coupling a connector to its implementation. */
    public SourceReaderRuntimeContext(
            TaskEnvironment environment,
            OperatorEventGateway eventGateway,
            Consumer<Throwable> asyncFailureHandler,
            IntConsumer completedSplits,
            SchemaCoordinator schemaCoordinator) {
        this.completedSplits = Objects.requireNonNull(completedSplits, "completedSplits");
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
        this.eventGateway = Objects.requireNonNull(eventGateway, "eventGateway must not be null");
        this.asyncFailureHandler = Objects.requireNonNull(asyncFailureHandler, "asyncFailureHandler must not be null");
        this.schemaCoordinator = schemaCoordinator;
    }

    @Override
    public Configuration getConfiguration() {
        return environment.configuration();
    }

    @Override
    public int getIndexOfSubtask() {
        return environment.taskInfo().subtaskIndex();
    }

    @Override
    public int currentParallelism() {
        return environment.taskInfo().parallelism();
    }

    @Override
    public void reportCompletedSplits(int count) {
        completedSplits.accept(count);
    }

    @Override
    public void sendSourceEventToCoordinator(SourceEvent event) {
        send(new SourceEventWrapper(Objects.requireNonNull(event, "event")));
    }

    @Override
    public void sendSplitRequest() {
        send(new RequestSplitEvent(getIndexOfSubtask(), environment.taskInfo().attemptNumber()));
    }

    @Override
    public CompletionStage<Long> sendSchemaChangeRequestToCoordinator(SchemaChangeRequest request) {
        if (schemaCoordinator == null) {
            return SourceReaderContext.super.sendSchemaChangeRequestToCoordinator(request);
        }
        if (environment.isCancellationRequested()) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new java.util.concurrent.CancellationException("Source task is cancelling"));
        }
        CompletionStage<Long> result =
                schemaCoordinator.applySchemaChange(Objects.requireNonNull(request, "request"), getIndexOfSubtask());
        result.whenComplete((unused, failure) -> {
            if (failure != null && !environment.isCancellationRequested()) {
                asyncFailureHandler.accept(failure);
            }
        });
        return result;
    }

    @Override
    public CompletionStage<Long> sendSourceSchemaChange(SourceSchemaChange change) {
        if (schemaCoordinator == null) {
            return SourceReaderContext.super.sendSourceSchemaChange(change);
        }
        CompletionStage<Long> result = schemaCoordinator.applySourceSchemaChange(
                Objects.requireNonNull(change, "change"), getIndexOfSubtask());
        result.whenComplete((unused, failure) -> {
            if (failure != null && !environment.isCancellationRequested()) {
                asyncFailureHandler.accept(failure);
            }
        });
        return result;
    }

    @Override
    public Optional<Schema> restoredSourceSchema(TableId sourceTable) {
        return schemaCoordinator == null ? Optional.empty() : schemaCoordinator.sourceSchema(sourceTable);
    }

    @Override
    public long sourceSchemaVersion(TableId sourceTable) {
        return schemaCoordinator == null ? 0 : schemaCoordinator.sourceSchemaVersion(sourceTable);
    }

    private void send(io.yak.ops.flow.runtime.operators.coordination.OperatorEvent event) {
        if (environment.isCancellationRequested()) {
            return;
        }
        CompletionStage<Void> response;
        try {
            response = Objects.requireNonNull(
                    eventGateway.sendEventToCoordinator(event), "OperatorEventGateway returned null");
        } catch (Throwable failure) {
            asyncFailureHandler.accept(failure);
            return;
        }
        response.whenComplete((unused, failure) -> {
            if (failure != null && !environment.isCancellationRequested()) {
                asyncFailureHandler.accept(failure);
            }
        });
    }
}
