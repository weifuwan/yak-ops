package io.yak.ops.flow.runtime.state;

import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.flow.runtime.checkpoint.CheckpointSnapshot;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Mailbox-confined, in-memory operator state with versioned serialization at its API boundary.
 * A checkpoint copies the named values; the checkpoint store owns durability.
 *
 * <p>Keyed state is scoped to the subtask's fixed key-group range, with serialized keys kept
 * distinct even when their hashes collide. Rescaling, TTL, asynchronous state and remote
 * backends are not implemented.
 */
public final class OperatorStateBackend {

    private final Map<String, CheckpointSnapshot.SerializedState> values = new TreeMap<>();
    private final KeyGroupRange keyGroups;
    private final int maxParallelism;
    private final boolean keyedInput;

    public OperatorStateBackend(
            Map<String, CheckpointSnapshot.SerializedState> restored,
            RuntimeTaskInfo taskInfo, boolean keyedInput) {
        Objects.requireNonNull(restored, "restored");
        Objects.requireNonNull(taskInfo, "taskInfo");
        values.putAll(restored);
        this.maxParallelism = taskInfo.maxParallelism();
        this.keyGroups = KeyGroupRangeAssignment.computeKeyGroupRangeForOperatorIndex(
                maxParallelism, taskInfo.parallelism(), taskInfo.subtaskIndex());
        this.keyedInput = keyedInput;
        if (!keyedInput && values.keySet().stream().anyMatch(name -> name.startsWith("keyed/"))) {
            throw new IllegalStateException("Cannot restore keyed state without a KEYED input edge");
        }
    }

    public <T> Optional<T> get(String name, SimpleVersionedSerializer<T> serializer) throws IOException {
        CheckpointSnapshot.SerializedState state = values.get(operatorName(name));
        if (state == null) {
            return Optional.empty();
        }
        return Optional.of(Objects.requireNonNull(
                serializer.deserialize(state.version(), state.bytes()), "Deserializer returned null"));
    }

    public <T> void put(String name, T value, SimpleVersionedSerializer<T> serializer) throws IOException {
        putSerialized(operatorName(name), value, serializer);
    }

    public void remove(String name) {
        values.remove(operatorName(name));
    }

    /** Used for variable-sized StatefulSinkWriter lists, preventing stale tail entries. */
    public void removeByPrefix(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        values.keySet().removeIf(name -> name.startsWith("operator/" + prefix));
    }

    public Set<String> names() {
        return Set.copyOf(values.keySet());
    }

    public <K, T> Optional<T> getKeyed(
            String name, K key, SimpleVersionedSerializer<K> keySerializer,
            SimpleVersionedSerializer<T> stateSerializer) throws IOException {
        String stateName = keyedName(name, key, keySerializer);
        CheckpointSnapshot.SerializedState state = values.get(stateName);
        if (state == null) {
            return Optional.empty();
        }
        return Optional.of(Objects.requireNonNull(
                stateSerializer.deserialize(state.version(), state.bytes()), "Deserializer returned null"));
    }

    public <K, T> void putKeyed(
            String name, K key, SimpleVersionedSerializer<K> keySerializer,
            T value, SimpleVersionedSerializer<T> stateSerializer) throws IOException {
        putSerialized(keyedName(name, key, keySerializer), value, stateSerializer);
    }

    /** All bytes are defensively copied by SerializedState; no live operator instance is retained. */
    public Map<String, CheckpointSnapshot.SerializedState> snapshot() {
        return Map.copyOf(new LinkedHashMap<>(values));
    }

    private <T> void putSerialized(
            String name, T value, SimpleVersionedSerializer<T> serializer) throws IOException {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(serializer, "serializer");
        values.put(name, new CheckpointSnapshot.SerializedState(
                serializer.getVersion(), serializer.serialize(value)));
    }

    private <K> String keyedName(String name, K key, SimpleVersionedSerializer<K> serializer)
            throws IOException {
        if (!keyedInput) {
            throw new UnsupportedOperationException("Keyed state requires a KEYED input edge");
        }
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(serializer, "serializer");
        int group = KeyGroupRangeAssignment.assignToKeyGroup(key, maxParallelism);
        if (!keyGroups.contains(group)) {
            throw new IllegalArgumentException("Key belongs to another subtask's KeyGroup: " + group);
        }
        byte[] keyBytes = serializer.serialize(key);
        if (keyBytes.length > 4096) {
            throw new IOException("Serialized state key exceeds 4096 bytes");
        }
        return "keyed/" + validateName(name) + "/" + group + "/" + serializer.getVersion()
                + "/" + Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);
    }

    private static String operatorName(String name) {
        return "operator/" + validateName(name);
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank() || name.contains("/") || name.length() > 128) {
            throw new IllegalArgumentException("Invalid state name");
        }
        return name;
    }
}
