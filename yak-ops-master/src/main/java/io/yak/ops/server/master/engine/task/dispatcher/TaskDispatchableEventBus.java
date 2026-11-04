package io.yak.ops.server.master.engine.task.dispatcher;

import io.yak.ops.eventbus.AbstractDelayEventBus;
import io.yak.ops.server.master.engine.task.dispatcher.event.TaskDispatchableEvent;

public class TaskDispatchableEventBus<V extends TaskDispatchableEvent<T>, T extends Comparable<T>>
        extends
            AbstractDelayEventBus<V> {

    public void add(V v) {
        super.publish(v);
    }

    @Override
    public V take() throws InterruptedException {
        return super.take();
    }

    // Only use in test
    public int size() {
        return delayEventQueue.size();
    }

    // Only use in test
    public void clear() {
        delayEventQueue.clear();
    }
}
