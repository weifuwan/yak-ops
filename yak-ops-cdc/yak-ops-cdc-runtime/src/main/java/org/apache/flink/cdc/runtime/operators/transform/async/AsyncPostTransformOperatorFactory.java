package org.apache.flink.cdc.runtime.operators.transform.async;

import org.apache.flink.annotation.Internal;
import org.apache.flink.cdc.common.event.Event;
import org.apache.flink.cdc.runtime.operators.AsyncWaitOperatorAdapter;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.operators.StreamOperator;
import org.apache.flink.streaming.api.operators.StreamOperatorParameters;
import org.apache.flink.streaming.api.operators.async.AsyncWaitOperatorFactory;

/** Factory for an ordered async post-transform operator with state-consistent checkpoints. */
@Internal
public class AsyncPostTransformOperatorFactory extends AsyncWaitOperatorFactory<Event, Event> {

    private static final long serialVersionUID = 1L;

    private final AsyncPostTransformFunction asyncFunction;
    private final long timeout;
    private final int capacity;

    public AsyncPostTransformOperatorFactory(
            AsyncPostTransformFunction asyncFunction, long timeout, int capacity) {
        super(asyncFunction, timeout, capacity, AsyncDataStream.OutputMode.ORDERED);
        this.asyncFunction = asyncFunction;
        this.timeout = timeout;
        this.capacity = capacity;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends StreamOperator<Event>> T createStreamOperator(
            StreamOperatorParameters<Event> parameters) {
        return (T)
                new AsyncWaitOperatorAdapter<>(
                        parameters,
                        asyncFunction,
                        timeout,
                        capacity,
                        processingTimeService,
                        getMailboxExecutor());
    }

    @Override
    public Class<? extends StreamOperator> getStreamOperatorClass(ClassLoader classLoader) {
        return AsyncWaitOperatorAdapter.class;
    }
}
