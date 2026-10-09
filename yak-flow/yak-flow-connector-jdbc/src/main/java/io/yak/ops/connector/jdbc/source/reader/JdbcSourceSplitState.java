package io.yak.ops.connector.jdbc.source.reader;

import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import java.util.Objects;

/** Mailbox-confined progress state, advanced only after record emission succeeds. */
public final class JdbcSourceSplitState {

    private final JdbcSourceSplit split;
    private Long lastEmittedKey;

    public JdbcSourceSplitState(JdbcSourceSplit split) {
        this.split = Objects.requireNonNull(split, "split");
        this.lastEmittedKey = split.lastEmittedKey();
    }

    public void onRecordEmitted(Long key) {
        if (split.splitColumn() == null) {
            return;
        }
        if (key == null || (lastEmittedKey != null && key <= lastEmittedKey)) {
            throw new IllegalArgumentException("JDBC split key must advance monotonically");
        }
        lastEmittedKey = key;
    }

    public JdbcSourceSplit toCheckpointSplit() {
        return split.withLastEmittedKey(lastEmittedKey);
    }
}
