package org.apache.flink.cdc.connectors.mysql.source.assigners.state;

import javax.annotation.Nullable;

/**
 * A checkpoint of the current state of the containing the currently pending splits that are not yet
 * assigned.
 */
public abstract class PendingSplitsState {
    /**
     * The splits are frequently serialized into checkpoints. Caching the byte representation makes
     * repeated serialization cheap. This field is used by {@link PendingSplitsStateSerializer}.
     */
    @Nullable transient byte[] serializedFormCache;
}
