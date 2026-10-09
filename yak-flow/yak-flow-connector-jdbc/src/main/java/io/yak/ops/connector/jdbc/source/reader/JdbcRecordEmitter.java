package io.yak.ops.connector.jdbc.source.reader;

import io.yak.ops.connector.base.source.reader.RecordEmitter;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.data.TableRecord;

/**
 * Emits JDBC records on the mailbox before advancing the checkpointed primary-key cursor.
 *
 * <p>Fetcher prefetch never updates checkpoint positions; an output failure leaves the position
 * unchanged so the record can be replayed on a later attempt.
 */
public final class JdbcRecordEmitter
        implements RecordEmitter<JdbcRecordAndPosition, TableRecord, JdbcSourceSplitState> {

    @Override
    public void emitRecord(
            JdbcRecordAndPosition element, ReaderOutput<TableRecord> output, JdbcSourceSplitState splitState)
            throws Exception {
        output.collect(element.record());
        splitState.onRecordEmitted(element.lastKey());
    }
}
