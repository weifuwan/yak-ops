package io.yak.ops.worker.task.executor.eventbus;

import io.yak.ops.worker.task.executor.events.TaskExecutorLifecycleEvent;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Process-local lifecycle event bus; no RPC or remote ACK transport. */
public final class TaskExecutorEventBus {
    private final ConcurrentLinkedQueue<TaskExecutorLifecycleEvent> queue = new ConcurrentLinkedQueue<>();
    public void publish(TaskExecutorLifecycleEvent event) { queue.add(event); }
    public Optional<TaskExecutorLifecycleEvent> poll() { return Optional.ofNullable(queue.poll()); }
    public boolean isEmpty() { return queue.isEmpty(); }
}
