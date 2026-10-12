package io.yak.ops.flow.runtime.checkpoint;

import io.yak.ops.core.api.connector.source.SourceSplit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Coordinator-side portion of one aligned Source checkpoint.
 *
 * <p>This state alone does not establish a completed job checkpoint. The runtime must
 * persist it together with Reader and downstream state at the same checkpoint boundary
 * before acknowledging completion to the Source.
 *
 * <p>Connector-defined split state must be independently serializable and restorable.
 *
 * @param <SplitT> the source split type
 * @param <EnumStateT> the enumerator checkpoint state type
 */
public record SourceCoordinatorCheckpoint<SplitT extends SourceSplit, EnumStateT>(
        long checkpointId,
        EnumStateT enumeratorState,
        Map<Integer, List<SplitT>> assignedSinceLastCompletedCheckpoint) {

    public SourceCoordinatorCheckpoint {
        if (checkpointId < 0) {
            throw new IllegalArgumentException("checkpointId must not be negative");
        }
        Objects.requireNonNull(enumeratorState, "enumeratorState must not be null");
        Objects.requireNonNull(
                assignedSinceLastCompletedCheckpoint, "assignedSinceLastCompletedCheckpoint must not be null");
        Map<Integer, List<SplitT>> copy = new LinkedHashMap<>();
        assignedSinceLastCompletedCheckpoint.forEach((id, splits) -> {
            if (id == null || id < 0) {
                throw new IllegalArgumentException("Invalid subtask index");
            }
            copy.put(id, List.copyOf(splits));
        });
        assignedSinceLastCompletedCheckpoint = Map.copyOf(copy);
    }
}
