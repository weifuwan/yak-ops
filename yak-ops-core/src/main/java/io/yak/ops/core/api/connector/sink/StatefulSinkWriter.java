package io.yak.ops.core.api.connector.sink;

import java.util.List;

/**
 * Optional writer-state contract for a future stateful checkpoint coordinator.
 *
 * <p>Declaring state does not make the current quiescent Source→Sink checkpoint persist it.
 * Runtime must reject stateful writers when checkpointing is requested until state integration.
 */
public interface StatefulSinkWriter<T, StateT> extends SinkWriter<T> {

    List<StateT> snapshotState(long checkpointId) throws Exception;
}
