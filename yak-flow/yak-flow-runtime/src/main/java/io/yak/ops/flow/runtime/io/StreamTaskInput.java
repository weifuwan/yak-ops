package io.yak.ops.flow.runtime.io;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import java.util.concurrent.CompletableFuture;

/**
 * Common non-blocking input protocol for SourceOperator and local InputGate.
 *
 * <p>The StreamTask mailbox drives one step at a time. Only the owning mailbox thread calls
 * emitNext; when NOTHING_AVAILABLE is returned, getAvailableFuture must arrange a wakeup.
 */
public interface StreamTaskInput<T> {

    int getInputIndex();

    InputStatus emitNext(ReaderOutput<T> output) throws Exception;

    CompletableFuture<Void> getAvailableFuture();
}
