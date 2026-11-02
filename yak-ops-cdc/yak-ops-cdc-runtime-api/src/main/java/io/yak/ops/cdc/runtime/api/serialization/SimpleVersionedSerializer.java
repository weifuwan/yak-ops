package io.yak.ops.cdc.runtime.api.serialization;

import java.io.IOException;

/** Versioned serialization contract, adapted from Flink 1.20.3 SimpleVersionedSerializer. */
public interface SimpleVersionedSerializer<T> {
    int getVersion();

    byte[] serialize(T value) throws IOException;

    T deserialize(int version, byte[] serialized) throws IOException;
}
