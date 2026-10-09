package io.yak.ops.core.api.connector.sink;

import java.util.List;

/**
* Optional Writer state snapshot capability for aligned checkpoints.
*
* <p>Snapshots can be persisted only when the Sink also implements
* {@link SupportsWriterState} with a matching versioned serializer and restore method.
* A snapshot is not a transaction commit or an exactly-once guarantee.
*
* @param <T> the input record type
* @param <StateT> the serialized Writer state type
*/
public interface StatefulSinkWriter<T, StateT> extends SinkWriter<T> {

    /**
    * Takes a snapshot after the Writer's checkpoint flush has completed.
    *
    * @param checkpointId the checkpoint currently being aligned
    * @return the state values to persist for recovery
    * @throws Exception if capturing Writer state fails
    */
    List<StateT> snapshotState(long checkpointId) throws Exception;
}
