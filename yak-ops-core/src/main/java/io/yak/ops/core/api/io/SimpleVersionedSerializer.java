package io.yak.ops.core.api.io;

import java.io.IOException;

/**
* Versioned binary serialization contract for splits and checkpoint state.
*
* <p>Connectors own encoding and cross-version compatibility; the runtime owns durable
* storage and transport. Live connections and secrets must not be serialized into state.
*
* @param <T> the split or state type being serialized
* @author weifuwan
*/
public interface SimpleVersionedSerializer<T> {

    /** Returns the version of the format written by this serializer. */
    int getVersion();

    /**
    * Serializes the value into an independent byte array.
    *
    * @param value the split or state value
    * @return bytes safe to retain after this method returns
    * @throws IOException if encoding fails
    */
    byte[] serialize(T value) throws IOException;

    /**
    * Deserializes a value using the format version stored with its bytes.
    *
    * @param version the version recorded when the value was written
    * @param serialized the serialized bytes
    * @return the restored value
    * @throws IOException if the version or encoded data is unsupported
    */
    T deserialize(int version, byte[] serialized) throws IOException;
}
