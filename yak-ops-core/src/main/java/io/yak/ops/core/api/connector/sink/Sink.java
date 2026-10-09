package io.yak.ops.core.api.connector.sink;

/**
 * Reusable definition that creates a separate {@link SinkWriter} for each execution attempt.
 *
 * <p>A Sink must not retain an active Writer or connection between submissions. The
 * {@link WriterInitContext} exposes the owning subtask and its effective configuration.
 * Stateful recovery requires {@link SupportsWriterState}; this API does not promise
 * transactional commits or exactly-once delivery.
 *
 * @param <T> the input record type
 */
@FunctionalInterface
public interface Sink<T> {

    /**
 * Creates a Writer for the current subtask and attempt.
 *
 * @param context the owning subtask and effective execution configuration
 * @return a new, non-null Writer that is not shared with another active task
 * @throws Exception if Writer creation fails
 */
    SinkWriter<T> createWriter(WriterInitContext context) throws Exception;
}
