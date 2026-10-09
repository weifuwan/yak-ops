package io.yak.ops.flow.runtime.source.event;

import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.flow.runtime.operators.coordination.OperatorEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Source split assignment transport: only versioned, privately-owned serialized bytes cross the
 * Coordinator ↔ Reader gateway. A Reader must deserialize using its own Source serializer.
 */
public final class AddSplitEvent<SplitT extends SourceSplit> implements OperatorEvent {

    private final int serializerVersion;
    private final List<byte[]> serializedSplits;

    public AddSplitEvent(List<SplitT> splits, SimpleVersionedSerializer<SplitT> serializer) throws IOException {
        Objects.requireNonNull(serializer, "split serializer");
        Objects.requireNonNull(splits, "splits");
        if (splits.isEmpty()) {
            throw new IllegalArgumentException("Split assignment cannot be empty");
        }
        serializerVersion = serializer.getVersion();
        if (serializerVersion < 0) {
            throw new IllegalArgumentException("Split serializer version cannot be negative");
        }
        List<byte[]> bytes = new ArrayList<>(splits.size());
        for (SplitT split : splits) {
            if (split == null || split.splitId() == null || split.splitId().isBlank()) {
                throw new IllegalArgumentException("Split ID cannot be blank");
            }
            bytes.add(Objects.requireNonNull(serializer.serialize(split), "Serialized split bytes").clone());
        }
        serializedSplits = List.copyOf(bytes);
    }

    public int serializerVersion() {
        return serializerVersion;
    }

    public int splitCount() {
        return serializedSplits.size();
    }

    public List<SplitT> splits(SimpleVersionedSerializer<SplitT> serializer) throws IOException {
        Objects.requireNonNull(serializer, "split serializer");
        List<SplitT> restored = new ArrayList<>(serializedSplits.size());
        for (byte[] bytes : serializedSplits) {
            SplitT split = Objects.requireNonNull(
                    serializer.deserialize(serializerVersion, bytes.clone()), "Decoded SourceSplit");
            if (split.splitId() == null || split.splitId().isBlank()) {
                throw new IOException("Decoded SourceSplit has no ID");
            }
            restored.add(split);
        }
        return List.copyOf(restored);
    }
}
