package io.yak.ops.server.master.engine.task.dispatcher.event;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.eventbus.AbstractDelayEvent;

import java.util.concurrent.Delayed;

import lombok.Getter;

@Getter
public class TaskDispatchableEvent<V extends Comparable<V>> extends AbstractDelayEvent {

    protected final V data;

    protected final int dispatchTimes;

    public TaskDispatchableEvent(long delayTimeMills, V data) {
        this(delayTimeMills, data, 0);
    }

    public TaskDispatchableEvent(long delayTimeMills, V data, int dispatchTimes) {
        super(delayTimeMills);
        this.data = checkNotNull(data, "data is null");
        this.dispatchTimes = dispatchTimes;
    }

    @Override
    public int compareTo(Delayed other) {
        if (!(other instanceof TaskDispatchableEvent)) {
            throw new RuntimeException("The object being compared is not a TaskReadyForDispatchEvent.");
        }

        @SuppressWarnings("unchecked")
        final TaskDispatchableEvent<V> otherEvent = (TaskDispatchableEvent<V>) other;

        // For two retry events, we should compare the priority first, since the task delay time has already been
        // expired.
        if (dispatchTimes > 0 && otherEvent.dispatchTimes > 0) {
            int priorityCompareResult = data.compareTo(otherEvent.data);
            if (priorityCompareResult != 0) {
                return priorityCompareResult;
            }
            return super.compareTo(otherEvent);
        }

        // For two new events, we should compare the delay time first, since the delay time is not expired.
        // For two evens, if one is new another is retry, we should compare the delay time first
        int delayTimeCompareResult = super.compareTo(otherEvent);
        if (delayTimeCompareResult != 0) {
            return delayTimeCompareResult;
        }
        return data.compareTo(otherEvent.data);
    }
}
