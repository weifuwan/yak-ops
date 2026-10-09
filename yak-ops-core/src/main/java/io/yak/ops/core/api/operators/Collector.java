package io.yak.ops.core.api.operators;

/**
* Synchronous output channel for records produced by an operator.
*
* <p>The runtime owns forwarding and backpressure. Callers may emit zero or more records
* during processing but must not retain this Collector for asynchronous use.
* Downstream failure is propagated to the runtime for cleanup.
*
* @param <T> the emitted record type
* @author weifuwan
*/
@FunctionalInterface
public interface Collector<T> {

    /**
    * Emits a record to the next pipeline stage.
    *
    * @param record the record to emit
    * @throws Exception if forwarding or downstream processing fails
    */
    void collect(T record) throws Exception;
}
