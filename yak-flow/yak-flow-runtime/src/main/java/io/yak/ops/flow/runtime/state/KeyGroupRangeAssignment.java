package io.yak.ops.flow.runtime.state;

import java.util.Objects;

/**
 * Fixed KeyGroup identifiers decouple key ownership from a particular task parallelism.
 *
 * <p>Implements the same MurmurHash and key-group-to-subtask arithmetic as Flink's
 * KeyGroupRangeAssignment. State migration and rescaling are not available until operator state
 * persistence exists; the mapping is nevertheless stable across a parallelism change.
 */
public final class KeyGroupRangeAssignment {

    public static final int DEFAULT_MAX_PARALLELISM = 128;
    public static final int MAX_PARALLELISM = 32768;

    private KeyGroupRangeAssignment() {}

    public static int assignKeyToParallelOperator(Object key, int maxParallelism, int parallelism) {
        return computeOperatorIndexForKeyGroup(maxParallelism, parallelism, assignToKeyGroup(key, maxParallelism));
    }

    public static int assignToKeyGroup(Object key, int maxParallelism) {
        Objects.requireNonNull(key, "key");
        checkMaxParallelism(maxParallelism);
        return murmurHash(key.hashCode()) % maxParallelism;
    }

    public static int computeOperatorIndexForKeyGroup(int maxParallelism, int parallelism, int keyGroupId) {
        checkParallelism(maxParallelism, parallelism);
        if (keyGroupId < 0 || keyGroupId >= maxParallelism) {
            throw new IllegalArgumentException("Invalid key-group ID");
        }
        return (int) ((long) keyGroupId * parallelism / maxParallelism);
    }

    /** Compute the inclusive key-group range assigned to one subtask. */
    public static KeyGroupRange computeKeyGroupRangeForOperatorIndex(
            int maxParallelism, int parallelism, int subtaskIndex) {
        checkParallelism(maxParallelism, parallelism);
        if (subtaskIndex < 0 || subtaskIndex >= parallelism) {
            throw new IllegalArgumentException("Invalid subtask index");
        }
        int start = (int) (((long) subtaskIndex * maxParallelism + parallelism - 1) / parallelism);
        int end = (int) (((long) (subtaskIndex + 1) * maxParallelism - 1) / parallelism);
        return new KeyGroupRange(start, end);
    }

    public static void checkMaxParallelism(int maxParallelism) {
        if (maxParallelism <= 0 || maxParallelism > MAX_PARALLELISM) {
            throw new IllegalArgumentException("maxParallelism must be within 1..32768");
        }
    }

    private static void checkParallelism(int maxParallelism, int parallelism) {
        checkMaxParallelism(maxParallelism);
        if (parallelism <= 0 || parallelism > maxParallelism) {
            throw new IllegalArgumentException("parallelism must be within 1..maxParallelism");
        }
    }

    /** Murmur3 32-bit hash of a single integer, matching Flink's MathUtils.murmurHash. */
    public static int murmurHash(int code) {
        code *= 0xcc9e2d51;
        code = Integer.rotateLeft(code, 15);
        code *= 0x1b873593;
        code = Integer.rotateLeft(code, 13);
        code = code * 5 + 0xe6546b64;
        code ^= 4;
        code ^= code >>> 16;
        code *= 0x85ebca6b;
        code ^= code >>> 13;
        code *= 0xc2b2ae35;
        code ^= code >>> 16;
        if (code >= 0) {
            return code;
        }
        return code == Integer.MIN_VALUE ? 0 : -code;
    }
}
