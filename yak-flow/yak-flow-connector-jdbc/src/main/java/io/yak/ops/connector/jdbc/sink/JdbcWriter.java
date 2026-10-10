package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.connector.base.sink.writer.BatchFlushPolicy;
import io.yak.ops.connector.base.sink.writer.BatchingSinkWriterBase;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.data.TableRecord;

/**
 * Thin JDBC Writer over the shared mailbox-owned batching lifecycle.
 *
 * <p>Only JdbcOutputFormat owns a connection and pending records. There is no second
 * batch list, scheduler, retry worker or implicit close-time commit in this Writer.
 */
public final class JdbcWriter extends BatchingSinkWriterBase<TableRecord> {

    public JdbcWriter(JdbcOutputFormat output, BatchFlushPolicy policy, WriterInitContext context) {
        super(output, policy, context);
    }
}
