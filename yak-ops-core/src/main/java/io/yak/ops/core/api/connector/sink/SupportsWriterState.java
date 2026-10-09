package io.yak.ops.core.api.connector.sink;

import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import java.util.Collection;

/**
 * Optional Sink V2 state-restore capability. Writer state restoration is NOT wired into the current
 * checkpoint format; advertising this capability must never imply that the Runtime can restore it.
 */
public interface SupportsWriterState<T, StateT> {

    StatefulSinkWriter<T, StateT> restoreWriter(
            WriterInitContext context, Collection<StateT> restoredState) throws Exception;

    SimpleVersionedSerializer<StateT> getWriterStateSerializer();
}
