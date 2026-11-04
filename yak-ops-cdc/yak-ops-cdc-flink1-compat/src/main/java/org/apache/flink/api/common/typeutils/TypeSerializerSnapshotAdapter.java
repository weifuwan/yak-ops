package org.apache.flink.api.common.typeutils;

import org.apache.flink.annotation.Internal;

/**
 * Compatibility adapter for Flink 1.20. This class is part of the multi-version compatibility layer
 * that allows Flink CDC to work across different Flink versions.
 */
@Internal
public interface TypeSerializerSnapshotAdapter<T> extends TypeSerializerSnapshot<T> {}
