package io.yak.ops.flow.runtime.tasks.mailbox;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The StreamTask's control queue. Any thread may submit mail, but only its bound task thread may
 * consume mail. Quiescing rejects new actions; closing returns pending actions for cancellation.
 */
public interface TaskMailbox {

    enum State {
        OPEN,
        QUIESCED,
        CLOSED
    }

    void bindMailboxThread(Thread thread);

    boolean isMailboxThread();

    void put(Mail<?> mail);

    Mail<?> tryTake();

    /**
     * Block while the default action is unavailable and there is no mail.
     * A readiness or shutdown signal wakes this method without requiring a synthetic control mail.
     */
    Mail<?> takeOrWait(BooleanSupplier defaultActionAvailable) throws InterruptedException;

    void wakeup();

    void quiesce();

    List<Mail<?>> close();

    State getState();
}
