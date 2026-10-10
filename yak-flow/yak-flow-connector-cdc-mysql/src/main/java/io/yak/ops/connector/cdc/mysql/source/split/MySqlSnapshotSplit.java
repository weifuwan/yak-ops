package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.core.data.TableId;
import java.util.Objects;

/**
 * Bounded, half-open BIGINT-key snapshot range with a mailbox-emitted progress cursor.
 *
 * <p>The last emitted key is exclusive on restart, while the original lower and upper
 * bounds remain fixed. Resuming never relies on an unstable ResultSet row offset.
 *
 * @param splitId unique, stable split identifier
 * @param fingerprint immutable source definition identity
 * @param tableId physical source table
 * @param lowerInclusive nullable range lower bound
 * @param upperExclusive nullable range upper bound
 * @param lastEmittedKey nullable last successfully delivered key
 */
public record MySqlSnapshotSplit(
        String splitId,
        String fingerprint,
        TableId tableId,
        Long lowerInclusive,
        Long upperExclusive,
        Long lastEmittedKey)
        implements MySqlHybridSplit {

    public MySqlSnapshotSplit {
        if (splitId == null || splitId.isBlank() || fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("Snapshot split requires a stable ID and fingerprint");
        }
        Objects.requireNonNull(tableId, "tableId");
        if (lowerInclusive != null && upperExclusive != null && lowerInclusive >= upperExclusive) {
            throw new IllegalArgumentException("Snapshot split bounds must be strictly increasing");
        }
        if (lastEmittedKey != null
                && ((lowerInclusive != null && lastEmittedKey < lowerInclusive)
                        || (upperExclusive != null && lastEmittedKey >= upperExclusive))) {
            throw new IllegalArgumentException("Snapshot split cursor is outside its range");
        }
    }

    /** Saves a consumed key without changing the originally assigned range. */
    public MySqlSnapshotSplit withLastEmittedKey(long key) {
        if (lastEmittedKey != null && key <= lastEmittedKey) {
            throw new IllegalArgumentException("Snapshot key must advance monotonically");
        }
        return new MySqlSnapshotSplit(splitId, fingerprint, tableId, lowerInclusive, upperExclusive, key);
    }
}
