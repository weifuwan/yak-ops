package io.yak.ops.alert.service;

import java.util.concurrent.LinkedBlockingQueue;

public abstract class AbstractEventPendingQueue<T> implements EventPendingQueue<T> {

    private final LinkedBlockingQueue<T> pendingAlertQueue;

    private final int capacity;

    protected AbstractEventPendingQueue(int capacity) {
        this.capacity = capacity;
        this.pendingAlertQueue = new LinkedBlockingQueue<>(capacity);
    }

    @Override
    public void put(T alert) throws InterruptedException {
        pendingAlertQueue.put(alert);
    }

    @Override
    public T take() throws InterruptedException {
        return pendingAlertQueue.take();
    }

    @Override
    public int size() {
        return pendingAlertQueue.size();
    }

    @Override
    public int capacity() {
        return capacity;
    }

}
