package io.yak.ops.core.api.connector.source;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.SchemaChangeRequest;
import io.yak.ops.core.event.SourceSchemaChange;
import io.yak.ops.core.schema.Schema;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Minimal runtime context for one SourceReader.
 *
 * <p>Exposes subtask identity and effective configuration, not database connections,
 * application task metadata or particular split instances.
 *
 * @author weifuwan
 */
public interface SourceReaderContext {

    /** Returns an independent copy of the effective configuration for this subtask. */
    Configuration getConfiguration();

    /** Returns this reader's zero-based subtask index. */
    int getIndexOfSubtask();

    /** Returns the resolved parallelism of the owning source operator. */
    int currentParallelism();

    /** Reports fully consumed bounded source splits after their completion markers. */
    default void reportCompletedSplits(int count) {}

    /** Requests splits through the runtime's attempt-aware coordinator gateway. */
    void sendSplitRequest();

    /**
     * Sends a connector-defined event to the owning source coordinator.
     *
     * <p>Implementations without event routing may reject this optional operation.
     */
    default void sendSourceEventToCoordinator(SourceEvent event) {
        throw new UnsupportedOperationException("This context does not support SourceEvent transport");
    }

    /**
     * Requests a planned schema transition and completes only after all target Writers adopt it.
     *
     * <p>Implementations must not block the Source mailbox while awaiting this stage. A connector
     * must also stop emitting post-change rows until the stage completes, and must not advance its
     * recoverable source offset past the event before that acknowledgment.
     */
    default CompletionStage<Long> sendSchemaChangeRequestToCoordinator(SchemaChangeRequest request) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("This context does not support schema change coordination"));
    }

    /** Requests a source DDL transition; the runtime Sink selects and validates the target route. */
    default CompletionStage<Long> sendSourceSchemaChange(SourceSchemaChange change) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Source schema evolution is not enabled"));
    }

    /** Returns the Source schema committed alongside the recovered Binlog position, if any. */
    default Optional<Schema> restoredSourceSchema(TableId sourceTable) {
        return Optional.empty();
    }

    /** Returns the committed schema version used to identify native Event schema transitions. */
    default long sourceSchemaVersion(TableId sourceTable) {
        return 0;
    }
}
