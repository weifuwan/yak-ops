package io.yak.ops.flow.runtime.tasks.mailbox;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/** Posts control actions to the owning Task, returning a completion acknowledgment. */
public interface MailboxExecutor {

    <T> CompletableFuture<T> submit(Callable<T> action);
}
