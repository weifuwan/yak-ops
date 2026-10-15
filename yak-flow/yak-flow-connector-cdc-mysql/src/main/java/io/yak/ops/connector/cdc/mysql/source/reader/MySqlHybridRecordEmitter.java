package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.base.source.reader.RecordEmitter;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlLowWatermarkEvent;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.data.TableRecord;
import java.util.Objects;

/**
 * Emits snapshot rows and indivisible Binlog mutations before mutating mailbox checkpoint state.
 *
 * <p>The initial low-watermark event has no rows. Only after its Schema History and offset
 * are recorded does the coordinator receive a signal to begin planning snapshot splits.
 */
public final class MySqlHybridRecordEmitter
        implements RecordEmitter<MySqlHybridFetchedRecord, TableRecord, MySqlHybridSplitState> {

    private final SourceReaderContext context;
    private final MySqlHybridSplitReader splits;
    private final TableMetrics metrics;
    private final long generation;

    public MySqlHybridRecordEmitter(SourceReaderContext context, MySqlHybridSplitReader splits) {
        this(context, splits, new TableMetrics());
    }

    /** Creates the mailbox emitter with phase-aware, generation-fenced record measurements. */
    public MySqlHybridRecordEmitter(SourceReaderContext context, MySqlHybridSplitReader splits, TableMetrics metrics) {
        this.metrics = metrics;
        generation = metrics.generation();
        this.context = Objects.requireNonNull(context, "context");
        this.splits = Objects.requireNonNull(splits, "splits");
    }

    @Override
    public void emitRecord(
            MySqlHybridFetchedRecord event, ReaderOutput<TableRecord> output, MySqlHybridSplitState state)
            throws Exception {
        if (event.preparedSnapshot() != null) {
            state.prepareSnapshot(event.preparedSnapshot());
            return;
        }
        for (TableRecord row : event.rows()) {
            output.collect(row);
            metrics.read(generation, row.tableId(), state.binlog());
        }
        metrics.observed(generation);
        if (event.snapshotRowsEmitted() != null) {
            state.emitSnapshotRows(event.snapshotRowsEmitted());
        } else if (event.snapshotKey() != null) {
            state.emitSnapshotKey(event.snapshotKey());
        } else if (event.lowWatermark()) {
            state.captureLow(event.binlogOffset(), splits.snapshotHistory());
            context.sendSourceEventToCoordinator(new MySqlLowWatermarkEvent(event.binlogOffset()));
            context.sendSplitRequest();
        } else {
            state.emitBinlog(event.binlogOffset());
        }
    }
}
