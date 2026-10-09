package io.yak.ops.core.api.connector.source;

/**
* Receives records emitted by a SourceReader for delivery to downstream operators.
*
* <p>The runtime owns backpressure and forwarding. Event-time, watermark and split-specific
* outputs are not part of this contract.
*
* @param <T> the emitted record type
* @author weifuwan
*/
@FunctionalInterface
public interface ReaderOutput<T> {

    /**
    * Emits one record to the downstream pipeline.
    *
    * @param record the record produced by the reader
    * @throws Exception if backpressure or downstream processing fails
    */
    void collect(T record) throws Exception;
}
