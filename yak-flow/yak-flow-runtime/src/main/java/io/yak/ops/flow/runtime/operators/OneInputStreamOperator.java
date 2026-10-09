package io.yak.ops.flow.runtime.operators;

import io.yak.ops.core.api.operators.Collector;

/** A StreamOperator that consumes one input and synchronously emits zero or more outputs. */
public interface OneInputStreamOperator<IN, OUT> extends StreamOperator {

    void processElement(IN element, Collector<OUT> output) throws Exception;

    /** Emit trailing buffered records before downstream operators finish. */
    default void finish(Collector<OUT> output) throws Exception {
        finish();
    }
}
