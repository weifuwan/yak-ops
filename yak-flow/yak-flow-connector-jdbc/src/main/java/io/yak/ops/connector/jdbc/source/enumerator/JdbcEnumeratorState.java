package io.yak.ops.connector.jdbc.source.enumerator;

import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import java.util.List;
import java.util.Objects;

/**
 * A stable checkpoint of remaining tables and unassigned splits.
 *
 * <p>The table index is advanced only after all splits for that table were successfully planned.
 * A table being planned asynchronously is therefore re-planned after recovery.
 */
public record JdbcEnumeratorState(String sourceFingerprint, int nextTableIndex, List<JdbcSourceSplit> pendingSplits) {

    public JdbcEnumeratorState {
        if (sourceFingerprint == null || sourceFingerprint.isBlank() || nextTableIndex < 0) {
            throw new IllegalArgumentException("Invalid JDBC enumerator checkpoint");
        }
        pendingSplits = List.copyOf(Objects.requireNonNull(pendingSplits, "pendingSplits"));
    }
}
