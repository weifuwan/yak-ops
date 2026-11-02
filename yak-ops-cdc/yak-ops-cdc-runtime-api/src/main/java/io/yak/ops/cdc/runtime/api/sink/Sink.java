package io.yak.ops.cdc.runtime.api.sink;

import java.io.IOException;
import java.io.Serializable;

/**
 * Flink 1.20.3 Sink V2 factory contract for a standalone JVM.
 *
 * <p>The deprecated InitContext overload and Flink-specific mailbox/serialization services are
 * intentionally not carried over. The concrete writer and checkpoint semantics are preserved.
 */
public interface Sink<T> extends Serializable {
    SinkWriter<T> createWriter(WriterInitContext context) throws IOException;
}
