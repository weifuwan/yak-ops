package io.yak.ops.core.api.connector.sink;

import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import java.util.Collection;

/**
* Optional capability for restoring a stateful {@link SinkWriter} from versioned state.
*
* <p>The embedded runtime persists Writer state under a stable operator UID and restores
* it before processing new records. This contract does not support transactional commits,
* redistributing state across subtasks, rescaling or exactly-once delivery.
*
* @param <T> the input record type
* @param <StateT> the state type supported by the serializer
*/
public interface SupportsWriterState<T, StateT> {

    /**
    * Creates a Writer using the state saved by the most recent completed checkpoint.
    *
    * @param context the new execution attempt's task context
    * @param restoredState the state values saved for this operator subtask
    * @return a new Writer restored from the supplied state
    * @throws Exception if the state cannot be restored
    */
    StatefulSinkWriter<T, StateT> restoreWriter(WriterInitContext context, Collection<StateT> restoredState)
            throws Exception;

    /** Returns the versioned serializer for the Writer's persisted state. */
    SimpleVersionedSerializer<StateT> getWriterStateSerializer();
}
