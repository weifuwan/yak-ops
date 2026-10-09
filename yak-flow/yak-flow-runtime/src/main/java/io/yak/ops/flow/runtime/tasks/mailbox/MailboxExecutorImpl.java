package io.yak.ops.flow.runtime.tasks.mailbox;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/** Mailbox-backed executor; no additional thread or executor service is created. */
public final class MailboxExecutorImpl implements MailboxExecutor {

    private final TaskMailbox mailbox;

    public MailboxExecutorImpl(TaskMailbox mailbox) {
        this.mailbox = Objects.requireNonNull(mailbox, "mailbox");
    }

    @Override
    public <T> CompletableFuture<T> submit(Callable<T> action) {
        Mail<T> mail = new Mail<>(action);
        try {
            mailbox.put(mail);
        } catch (IllegalStateException failure) {
            mail.reject(failure);
        }
        return mail.reply();
    }
}
