package io.yak.ops.server.master.engine;

import io.yak.ops.eventbus.AbstractDelayEventBus;

import java.util.concurrent.atomic.AtomicInteger;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The event bus for workflow, this is used to store the whole event in a workflow instance include the task event and the workflow event.
 */
@Slf4j
@Getter
public class WorkflowEventBus extends AbstractDelayEventBus<AbstractLifecycleEvent> {

    private final WorkflowEventBusSummary workflowEventBusSummary = new WorkflowEventBusSummary();

    public void publish(final AbstractLifecycleEvent event) {
        super.publish(event);
        workflowEventBusSummary.increaseEventCount();
        log.info("Publish event: {}", event);
    }

    @Data
    @NoArgsConstructor
    public static final class WorkflowEventBusSummary {

        private AtomicInteger eventCount = new AtomicInteger();
        private AtomicInteger fireSuccessEventCount = new AtomicInteger();
        private AtomicInteger fireFailedEventCount = new AtomicInteger();

        public Integer increaseEventCount() {
            return eventCount.incrementAndGet();
        }

        public Integer increaseFireSuccessEventCount() {
            return fireSuccessEventCount.incrementAndGet();
        }

        public Integer decreaseFireSuccessEventCount() {
            return fireSuccessEventCount.decrementAndGet();
        }

        public Integer increaseFireFailedEventCount() {
            return fireFailedEventCount.incrementAndGet();
        }

        @Override
        public String toString() {
            return "WorkflowEventBusSummary{" +
                    "eventCount=" + eventCount +
                    ", fireSuccessEventCount=" + fireSuccessEventCount +
                    ", fireFailedEventCount=" + fireFailedEventCount +
                    '}';
        }
    }
}
