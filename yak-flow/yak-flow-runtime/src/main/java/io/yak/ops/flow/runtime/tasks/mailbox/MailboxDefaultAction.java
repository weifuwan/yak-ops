package io.yak.ops.flow.runtime.tasks.mailbox;

/**
 * Input processing action interleaved with control mails by the mailbox thread.
 * Suspend this action while input is unavailable, without suspending control mail processing.
 */
@FunctionalInterface
public interface MailboxDefaultAction {

    void runDefaultAction(Controller controller) throws Exception;

    interface Controller {
        void allActionsCompleted();

        Suspension suspendDefaultAction();
    }

    @FunctionalInterface
    interface Suspension {
        void resume();
    }
}
