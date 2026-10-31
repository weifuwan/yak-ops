package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.base.source.reader.RecordEmitter;
import io.yak.ops.connector.cdc.base.source.reader.SchemaChangeHandler;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlLowWatermarkEvent;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlFetchedRecord;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitState;
import io.yak.ops.connector.cdc.mysql.source.utils.MySqlChangeEventIds;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.TableSchema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Emits snapshot rows and indivisible Binlog mutations before mutating mailbox checkpoint state.
 *
 * <p>The initial low-watermark event has no rows. Only after its Schema History and offset
 * are recorded does the coordinator receive a signal to begin planning snapshot splits.
 */
public final class MySqlRecordEmitter implements RecordEmitter<MySqlFetchedRecord, TableRecord, MySqlSplitState> {

    private final SourceReaderContext context;
    private final SchemaChangeHandler schemaChanges;
    private final MySqlSplitReader splits;
    private final TableMetrics metrics;
    private final long generation;
    private final String sourceId;

    public MySqlRecordEmitter(
            MySqlSourceReaderContext readerContext, MySqlSplitReader splits, TableMetrics metrics, String sourceId) {
        this.schemaChanges =
                Objects.requireNonNull(readerContext, "readerContext").schemaChanges();
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        generation = metrics.generation();
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.context = readerContext.getSourceReaderContext();
        this.splits = Objects.requireNonNull(splits, "splits");
    }

    @Override
    public void emitRecord(MySqlFetchedRecord event, ReaderOutput<TableRecord> output, MySqlSplitState state)
            throws Exception {
        if (event.schemaChange() != null) {
            schemaChanges.submitSchemaChange(event.schemaChange(), () -> state.emitBinlog(event.binlogOffset()));
            return;
        }
        if (event.preparedSnapshot() != null) {
            // Incremental Snapshot commits the completed split after all normalized rows
            // have been emitted together in this mailbox operation.
            MySqlSnapshotSplit prepared = event.preparedSnapshot();
            List<TableRecord> rows = prepared.output().rows();
            // A split can be re-scanned after a failed mailbox attempt. Assigning
            // identities by output position would reuse an ID for a different row
            // when the reconstructed result contains inserted/deleted keys.
            // Flink CDC's backfill uses physical keys for keyed images and full-row
            // identity for keyless ones; use the same distinction here.
            TableSchema schema = splits.snapshotSchema(prepared.tableId());
            Map<String, Integer> occurrences = new HashMap<>();
            for (TableRecord row : rows) {
                String rowIdentity = MySqlChangeEventIds.fromSnapshot(row, schema);
                int occurrence = occurrences.merge(rowIdentity, 1, Integer::sum) - 1;
                ChangeEventId identity = new ChangeEventId(
                        sourceId, "snapshot:" + state.snapshotSplitId() + ":" + rowIdentity + ":" + occurrence, 0, 1);
                output.collect(row.withChangeEventId(identity));
                metrics.read(generation, row.tableId(), false);
            }
            state.prepareSnapshot(prepared);
            metrics.observed(generation);
            return;
        }
        int count = event.rows().size();
        boolean selected = true;
        for (int index = 0; index < count; index++) {
            TableRecord row = event.rows().get(index);
            boolean accepted = splits.tableOptions(row.tableId()).accepts(row.rowKind());
            if (index != 0 && accepted != selected) {
                throw new IllegalStateException("A Binlog mutation cannot be partially selected");
            }
            selected = accepted;
        }
        String eventPosition = event.binlogOffset() != null && !event.lowWatermark()
                ? MySqlChangeEventIds.fromBinlog(event.binlogOffset())
                : null;
        for (int index = 0; index < count; index++) {
            TableRecord row = event.rows().get(index);
            ChangeEventId identity =
                    eventPosition == null ? null : new ChangeEventId(sourceId, eventPosition, index, count);
            if (selected) output.collect(identity == null ? row : row.withChangeEventId(identity));
            metrics.read(generation, row.tableId(), state.binlog());
        }
        metrics.observed(generation);
        if (event.lowWatermark()) {
            state.captureLow(event.binlogOffset(), splits.snapshotHistory());
            context.sendSourceEventToCoordinator(new MySqlLowWatermarkEvent(event.binlogOffset()));
            context.sendSplitRequest();
        } else {
            state.emitBinlog(event.binlogOffset());
        }
    }
}
