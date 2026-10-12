package io.yak.ops.flow.runtime.io.partition;

import java.util.concurrent.CompletableFuture;

/**
 * Marks the last data record on one local channel without closing its control path.
 *
 * <p>The consumer acknowledges this event after processing all preceding records.
 */
final class EndOfData {

    private final CompletableFuture<Void> acknowledgment = new CompletableFuture<>();

    CompletableFuture<Void> acknowledgment() {
        return acknowledgment;
    }

    void acknowledge() {
        acknowledgment.complete(null);
    }

    void fail(Throwable cause) {
        acknowledgment.completeExceptionally(cause);
    }
}
