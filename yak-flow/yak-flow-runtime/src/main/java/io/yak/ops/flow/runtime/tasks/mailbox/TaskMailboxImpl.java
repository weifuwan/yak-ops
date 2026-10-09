package io.yak.ops.flow.runtime.tasks.mailbox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/**
 * Single-consumer FIFO mailbox. A condition prevents lost wakeups between input availability,
 * coordinator control events and cancellation. Data backpressure remains in RecordChannel.
 */
public final class TaskMailboxImpl implements TaskMailbox {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition mailAvailable = lock.newCondition();
    private final Deque<Mail<?>> queue = new ArrayDeque<>();
    private volatile Thread mailboxThread;
    private State state = State.OPEN;

    @Override
    public void bindMailboxThread(Thread thread) {
        Objects.requireNonNull(thread, "thread");
        lock.lock();
        try {
            if (mailboxThread != null || state != State.OPEN) {
                throw new IllegalStateException("Mailbox thread cannot be rebound");
            }
            mailboxThread = thread;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isMailboxThread() {
        return Thread.currentThread() == mailboxThread;
    }

    @Override
    public void put(Mail<?> mail) {
        Objects.requireNonNull(mail, "mail");
        lock.lock();
        try {
            if (state != State.OPEN) {
                throw new IllegalStateException("TaskMailbox no longer accepts mail: " + state);
            }
            queue.addLast(mail);
            mailAvailable.signal();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Mail<?> tryTake() {
        requireMailboxThread();
        lock.lock();
        try {
            if (state == State.CLOSED) {
                throw new IllegalStateException("TaskMailbox is closed");
            }
            return queue.pollFirst();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Mail<?> takeOrWait(BooleanSupplier defaultActionAvailable) throws InterruptedException {
        requireMailboxThread();
        Objects.requireNonNull(defaultActionAvailable, "defaultActionAvailable");
        lock.lockInterruptibly();
        try {
            while (queue.isEmpty() && state == State.OPEN && !defaultActionAvailable.getAsBoolean()) {
                mailAvailable.await();
            }
            if (state == State.CLOSED) {
                throw new IllegalStateException("TaskMailbox is closed");
            }
            return queue.pollFirst();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void wakeup() {
        lock.lock();
        try {
            mailAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void quiesce() {
        lock.lock();
        try {
            if (state == State.OPEN) {
                state = State.QUIESCED;
            }
            mailAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Mail<?>> close() {
        lock.lock();
        try {
            state = State.CLOSED;
            List<Mail<?>> pending = new ArrayList<>(queue);
            queue.clear();
            mailAvailable.signalAll();
            return pending;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public State getState() {
        lock.lock();
        try {
            return state;
        } finally {
            lock.unlock();
        }
    }

    private void requireMailboxThread() {
        if (!isMailboxThread()) {
            throw new IllegalStateException("Only the StreamTask's mailbox thread may consume mail");
        }
    }
}
