package io.yak.ops.eventbus;

import java.util.Optional;
import java.util.concurrent.DelayQueue;

/**
 * The event bus that supports delay event.
 */
public abstract class AbstractDelayEventBus<T extends AbstractDelayEvent> implements IEventBus<T> {

    protected final DelayQueue<T> delayEventQueue = new DelayQueue<>();

    @Override
    public void publish(final T event) {
        delayEventQueue.add(event);
    }

    @Override
    public Optional<T> poll() {
        return Optional.ofNullable(delayEventQueue.poll());
    }

    @Override
    public Optional<T> peek() {
        return Optional.ofNullable(delayEventQueue.peek());
    }

    @Override
    public T take() throws InterruptedException {
        return delayEventQueue.take();
    }

    @Override
    public Optional<T> remove() {
        return Optional.ofNullable(delayEventQueue.remove());
    }

    @Override
    public boolean isEmpty() {
        return delayEventQueue.isEmpty();
    }
}
