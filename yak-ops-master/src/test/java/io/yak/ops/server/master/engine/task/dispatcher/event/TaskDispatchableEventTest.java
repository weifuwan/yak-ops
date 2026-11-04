package io.yak.ops.server.master.engine.task.dispatcher.event;

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class TaskDispatchableEventTest {

    @Test
    void getDelay() {
        TaskDispatchableEvent<String> delayEntry = new TaskDispatchableEvent<>(5_000L, "Item");
        assertThat(delayEntry.getDelay(TimeUnit.NANOSECONDS))
                .isWithin(TimeUnit.NANOSECONDS.convert(500, TimeUnit.MILLISECONDS))
                .of(TimeUnit.NANOSECONDS.convert(5_000L, TimeUnit.MILLISECONDS));
    }

    @Test
    void priorityCompare() {
        TaskDispatchableEvent<String> highPriorityEntry =
                new TaskDispatchableEvent<>(15_000L, "1_HIGH");
        TaskDispatchableEvent<String> lowPriorityEntry = new TaskDispatchableEvent<>(5_000L, "3_LOW");
        assertThat(highPriorityEntry.compareTo(lowPriorityEntry) > 0).isTrue();
    }
}
