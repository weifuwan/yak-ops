package org.apache.flink.streaming.api.operators.collect;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.streaming.api.environment.CheckpointConfig;

/**
 * Compatibility adapter for Flink 1.20. This class is part of the multi-version compatibility layer
 * that allows Flink CDC to work across different Flink versions.
 */
@Internal
public class CollectResultIteratorAdapter<T> extends CollectResultIterator<T> {

    /**
     * Creates a CollectResultIteratorAdapter with the given operatorUid.
     *
     * <p>This constructor accepts a String operatorUid (typically from {@code
     * sink.getTransformation().getId()}) and converts it to a CompletableFuture&lt;OperatorID&gt;
     * for compatibility with Flink 1.x.
     *
     * @param operatorUid the operator ID string
     * @param serializer the type serializer
     * @param accumulatorName the accumulator name
     * @param checkpointConfig the checkpoint config
     * @param resultFetchTimeout the result fetch timeout
     */
    public CollectResultIteratorAdapter(
            String operatorUid,
            CollectSinkOperator<T> collectSinkOperator,
            TypeSerializer<T> serializer,
            String accumulatorName,
            CheckpointConfig checkpointConfig,
            long resultFetchTimeout) {
        super(
                collectSinkOperator.getOperatorIdFuture(),
                serializer,
                accumulatorName,
                checkpointConfig,
                resultFetchTimeout);
    }

    /**
     * Creates a CollectResultIteratorAdapter with the given operatorUid and buffer.
     *
     * @param buffer the collect result buffer
     * @param operatorUid the operator ID string
     * @param accumulatorName the accumulator name
     * @param retryMillis the retry interval in milliseconds
     */
    public CollectResultIteratorAdapter(
            AbstractCollectResultBuffer<T> buffer,
            String operatorUid,
            CollectSinkOperator<T> collectSinkOperator,
            String accumulatorName,
            int retryMillis) {
        super(buffer, collectSinkOperator.getOperatorIdFuture(), accumulatorName, retryMillis);
    }
}
