package io.yak.ops.connector.jdbc.source.reader;

import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import java.util.Objects;

/**
 * Tracks the last successfully emitted numeric primary key in the SourceReader mailbox.
 *
 * <p>Prefetched rows never advance this state. Non-keyed splits retain their full-split
 * replay semantics instead of inventing an unstable row-offset checkpoint.
 */
public final class JdbcSourceSplitState {

    private final JdbcSourceSplit split;
    private Long lastEmittedKey;

    public JdbcSourceSplitState(JdbcSourceSplit split) {
        this.split = Objects.requireNonNull(split, "split");
        this.lastEmittedKey = split.lastEmittedKey();
    }

    /**
     * Advances the checkpoint cursor only after downstream emission succeeds.
     *
     * @param key strictly increasing numeric key; ignored for unkeyed splits
     * @throws IllegalArgumentException if the keyed split cursor does not advance
     */
    public void onRecordEmitted(Long key) {
        if (split.splitColumn() == null) {
            return;
        }
        if (key == null || (lastEmittedKey != null && key <= lastEmittedKey)) {
            throw new IllegalArgumentException("JDBC split key must advance monotonically");
        }
        lastEmittedKey = key;
    }

    /**
     * Captures the latest mailbox-owned cursor without mutating the assigned split.
     *
     * @return a detached, checkpoint-ready split definition
     */
    public JdbcSourceSplit toCheckpointSplit() {
        return split.withLastEmittedKey(lastEmittedKey);
    }
}
