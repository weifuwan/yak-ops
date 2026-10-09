package io.yak.ops.core.api.connector.sink;

/**
 * Sink V2 writer factory. Unlike legacy Sink, the operator must provide a WriterInitContext.
 *
 * <p>Legacy Sink lambdas remain valid; this explicit extension avoids silently creating a new
 * writer without subtask and attempt identity.
 */
@FunctionalInterface
public interface SinkV2<T> extends Sink<T> {

    @Override
    SinkWriter<T> createWriter(WriterInitContext context) throws Exception;

    @Override
    default SinkWriter<T> createWriter() throws Exception {
        throw new UnsupportedOperationException("SinkV2 requires WriterInitContext");
    }
}
