package io.yak.ops.cdc.runtime.standalone;

import org.apache.flink.cdc.common.event.Event;

/**
 * Synchronous in-process transform. Schema and row events remain in their original order.
 * Transform implementations own any schema-change behavior they introduce.
 */
@FunctionalInterface
public interface EventTransformer {

    void process(Event event, Collector output) throws Exception;

    @FunctionalInterface
    interface Collector {
        void collect(Event event) throws Exception;
    }

    static EventTransformer identity() {
        return (event, output) -> output.collect(event);
    }
}
