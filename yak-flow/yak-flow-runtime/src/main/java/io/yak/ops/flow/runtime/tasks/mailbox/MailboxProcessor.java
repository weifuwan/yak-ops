package io.yak.ops.flow.runtime.tasks.mailbox;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Flink-style mailbox loop: control mails and the input default action run on one Task thread.
 * When input is unavailable, the default action suspends; control mails still execute. Availability
 * callbacks resume the action without polling or busy sleeping.
 */
public final class MailboxProcessor {

    private final TaskMailbox mailbox;
    private final MailboxDefaultAction defaultAction;
    private final Runnable checkForFailure;
    private final BooleanSupplier stopping;
    private final MailboxExecutor executor;
    private final MailboxDefaultAction.Controller controller = new ControllerImpl();

    private volatile boolean running = true;
    private volatile DefaultActionSuspension suspendedAction;

    public MailboxProcessor(
            TaskMailbox mailbox,
            MailboxDefaultAction defaultAction,
            Runnable checkForFailure,
            BooleanSupplier stopping) {
        this.mailbox = Objects.requireNonNull(mailbox, "mailbox");
        this.defaultAction = Objects.requireNonNull(defaultAction, "defaultAction");
        this.checkForFailure = Objects.requireNonNull(checkForFailure, "checkForFailure");
        this.stopping = Objects.requireNonNull(stopping, "stopping");
        this.executor = new MailboxExecutorImpl(mailbox);
    }

    public MailboxExecutor getMailboxExecutor() {
        return executor;
    }

    public void runMailboxLoop() throws Exception {
        mailbox.bindMailboxThread(Thread.currentThread());
        while (running) {
            checkForFailure.run();
            Mail<?> mail = mailbox.tryTake();
            if (mail != null) {
                mail.run();
                checkForFailure.run();
            }
            if (!running) {
                break;
            }
            if (suspendedAction == null) {
                // At most one mail before each available input action, preserving input fairness.
                defaultAction.runDefaultAction(controller);
            } else if (mail == null) {
                Mail<?> next = mailbox.takeOrWait(
                        () -> suspendedAction == null || !running || stopping.getAsBoolean());
                if (next != null) {
                    next.run();
                }
            }
        }
    }

    public void prepareClose() {
        running = false;
        mailbox.quiesce();
        mailbox.wakeup();
    }

    /** Return pending control acknowledgments that must be completed exceptionally by the owner. */
    public void close(Throwable cause) {
        prepareClose();
        for (Mail<?> mail : mailbox.close()) {
            mail.reject(cause);
        }
    }

    private final class ControllerImpl implements MailboxDefaultAction.Controller {

        @Override
        public void allActionsCompleted() {
            running = false;
            mailbox.wakeup();
        }

        @Override
        public MailboxDefaultAction.Suspension suspendDefaultAction() {
            if (!mailbox.isMailboxThread()) {
                throw new IllegalStateException("Only the mailbox thread may suspend input");
            }
            if (suspendedAction != null) {
                throw new IllegalStateException("Default action is already suspended");
            }
            DefaultActionSuspension suspension = new DefaultActionSuspension();
            suspendedAction = suspension;
            return suspension;
        }
    }

    private final class DefaultActionSuspension implements MailboxDefaultAction.Suspension {

        @Override
        public void resume() {
            if (suspendedAction == this) {
                suspendedAction = null;
                mailbox.wakeup();
            }
        }
    }
}
