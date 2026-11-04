package io.yak.ops.alert.service;

/**
 * The interface responsible for managing pending events.
 *
 * @param <T> the type of event
 */
public interface EventPendingQueue<T> {

    void put(T alert) throws InterruptedException;

    T take() throws InterruptedException;

    int size();

    int capacity();
}
