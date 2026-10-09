package io.yak.ops.flow.runtime.operators;

import io.yak.ops.core.api.operators.Collector;

/**
 * Operator that consumes one input stream on its owning task's mailbox thread.
 *
 * <p>The runtime serializes lifecycle and record processing. Outputs are sent synchronously
 * through Collector; the operator must not retain a Collector for use by another thread.
 * Only normal input completion invokes {@link #finish(Collector)}.
 *
 * @param <IN> the input record type
 * @param <OUT> the emitted record type
 */
public interface OneInputStreamOperator<IN, OUT> extends StreamOperator {

    /**
     * Processes one input record and synchronously emits zero or more output records.
     *
     * @param element the input record
     * @param output the downstream output collector
     * @throws Exception if processing or forwarding fails
     */
    void processElement(IN element, Collector<OUT> output) throws Exception;

    /**
     * Emits buffered records after normal end of input.
     *
     * <p>The default implementation invokes {@link #finish()} from StreamOperator.
     * Failure and cancellation do not invoke this completion callback.
     */
    default void finish(Collector<OUT> output) throws Exception {
        finish();
    }
}
