package io.yak.ops.flow.runtime.tasks.mailbox;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/** One control action executed on its owning StreamTask's mailbox thread. */
public final class Mail<T> {

    private final Callable<T> action;
    private final CompletableFuture<T> reply = new CompletableFuture<>();

    public Mail(Callable<T> action) {
        this.action = Objects.requireNonNull(action, "action");
    }

    public CompletableFuture<T> reply() {
        return reply.copy();
    }

    /** An acknowledgment is only successful after the action actually runs. */
    public void run() throws Exception {
        try {
            reply.complete(action.call());
        } catch (Throwable failure) {
            reply.completeExceptionally(failure);
            if (failure instanceof Exception exception) {
                throw exception;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(failure);
        }
    }

    /** Fail a control action that could not be executed before shutdown. */
    public void reject(Throwable cause) {
        reply.completeExceptionally(Objects.requireNonNull(cause, "cause"));
    }
}
