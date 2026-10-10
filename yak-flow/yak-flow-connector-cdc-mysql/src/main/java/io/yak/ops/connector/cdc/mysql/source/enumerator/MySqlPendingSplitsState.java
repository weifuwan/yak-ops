package io.yak.ops.connector.cdc.mysql.source.enumerator;

import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import java.util.Objects;

/**
 * Describes whether the single unbounded Binlog split is pending or reader-owned.
 *
 * <p>An assigned split is checkpointed by its Reader and Runtime assignment tracker,
 * not duplicated into the enumerator state.
 */
public record MySqlPendingSplitsState(
        String sourceFingerprint, boolean splitAssigned, MySqlBinlogSplit pendingSplit) {

    public MySqlPendingSplitsState {
        Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        if (sourceFingerprint.isBlank() || splitAssigned == (pendingSplit != null)) {
            throw new IllegalArgumentException("Invalid MySQL Binlog assignment state");
        }
        if (pendingSplit != null && !sourceFingerprint.equals(pendingSplit.definitionFingerprint())) {
            throw new IllegalArgumentException("MySQL Binlog split belongs to a different source");
        }
    }
}
