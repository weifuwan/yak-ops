package io.yak.ops.connector.base.sink.writer;

import java.time.Duration;
import java.util.Objects;

/**
 * Database-neutral limits for an output-owned batch.
 *
 * @param maxBatchSize maximum buffered records before synchronous flush
 * @param flushInterval optional processing-time flush interval; zero disables timed flush
 */
public record BatchFlushPolicy(int maxBatchSize, Duration flushInterval) {

    public BatchFlushPolicy {
        if (maxBatchSize <= 0) {
            throw new IllegalArgumentException("Sink batch size must be positive");
        }
        Objects.requireNonNull(flushInterval, "flushInterval");
        if (flushInterval.isNegative() || (!flushInterval.isZero() && flushInterval.toMillis() == 0)) {
            throw new IllegalArgumentException("Sink flush interval must be zero or at least one millisecond");
        }
    }

    public boolean hasTimedFlush() {
        return !flushInterval.isZero();
    }
}
