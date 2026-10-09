package io.yak.ops.flow.runtime.state;

/** Inclusive range of stable KeyGroup IDs owned by one physical subtask. */
public record KeyGroupRange(int start, int end) {

    public KeyGroupRange {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid key-group range");
        }
    }

    public boolean contains(int keyGroupId) {
        return keyGroupId >= start && keyGroupId <= end;
    }
}
