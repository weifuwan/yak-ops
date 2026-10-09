package io.yak.ops.core.api.connector.sink;

import java.util.List;

/**
 * Optional SinkWriter state snapshot contract.
 *
 * <p>The embedded aligned-checkpoint runtime serializes these values only when the Sink
 * also implements SupportsWriterState and supplies a versioned serializer and restore method.
 * Without that restore contract, checkpointed execution must reject a stateful writer.
 * Snapshots do not imply a transactional commit or exactly-once delivery.
 */
public interface StatefulSinkWriter<T, StateT> extends SinkWriter<T> {

    List<StateT> snapshotState(long checkpointId) throws Exception;
}
