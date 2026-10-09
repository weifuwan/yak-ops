package io.yak.ops.core.api.connector.sink;

import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import java.util.Collection;

/**
 * Versioned Sink V2 writer-state restoration.
 *
 * <p>In the embedded aligned-checkpoint runtime, each subtask's serialized writer state
 * is stored with its stable operator UID and restored before processing records.
 * This contract does not provide transaction commits, cross-subtask state redistribution,
 * dynamic rescaling or exactly-once delivery.
 */
public interface SupportsWriterState<T, StateT> {

    StatefulSinkWriter<T, StateT> restoreWriter(
            WriterInitContext context, Collection<StateT> restoredState) throws Exception;

    SimpleVersionedSerializer<StateT> getWriterStateSerializer();
}
