package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.source.reader.RecordEmitter;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.data.TableRecord;

/**
 * Emits one complete Debezium change on a single mailbox step, then advances the source offset.
 *
 * <p>An UPDATE_BEFORE and UPDATE_AFTER pair is never separated by a checkpoint barrier.
 * If downstream delivery fails, the source offset remains at its previous value.
 */
public final class MySqlCdcRecordEmitter implements RecordEmitter<BinlogEvent, TableRecord, MySqlCdcSplitState> {

    @Override
    public void emitRecord(BinlogEvent event, ReaderOutput<TableRecord> output, MySqlCdcSplitState state) {
        for (TableRecord record : event.records()) {
            output.collect(record);
        }
        state.onEmitted(event.offset());
    }
}
