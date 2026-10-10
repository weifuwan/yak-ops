package io.yak.ops.connector.jdbc.sink.writer;

import io.yak.ops.connector.base.sink.writer.BatchFlushPolicy;
import io.yak.ops.connector.base.sink.writer.BatchingSinkWriterBase;
import io.yak.ops.connector.jdbc.internal.JdbcOutputFormat;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.data.TableRecord;

/**
 * Bridges the shared mailbox-owned batch lifecycle to JDBC output and cancellation.
 *
 * <p>The Writer contains no independent record queue, scheduler or retry loop.
 * {@link JdbcOutputFormat} owns the connection and the only buffered-record owner; successful
 * checkpoint/end-of-input flushes commit synchronously, while failure, cancel and close
 * never implicitly commit pending records.
 */
public final class JdbcWriter extends BatchingSinkWriterBase<TableRecord> {

    public JdbcWriter(JdbcOutputFormat output, BatchFlushPolicy policy, WriterInitContext context) {
        super(output, policy, context);
    }
}
