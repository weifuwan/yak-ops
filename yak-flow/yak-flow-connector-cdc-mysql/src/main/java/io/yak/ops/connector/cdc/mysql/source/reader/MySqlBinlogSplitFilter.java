package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffsetOrder;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.TableSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Filters Binlog changes already contained in per-chunk normalized snapshot output.
 *
 * <p>Follows Flink CDC's finished-split High Watermark filtering, rather than dropping
 * all changes before one global cutoff. A primary-key move can have a different fence
 * for its old and new keys; if only one half survives, convert that half to standalone
 * DELETE or INSERT so the downstream JDBC Sink never sees an incomplete UPDATE pair.
 */
public final class MySqlBinlogSplitFilter {

    private final Map<TableId, List<FinishedSnapshotSplitInfo>> completed = new LinkedHashMap<>();
    private final Map<TableId, Integer> keyPositions = new LinkedHashMap<>();

    /**
     * Builds an immutable table-indexed filter using the completed initial snapshot.
     *
     * @param schemas frozen source table schemas
     * @param finished fully normalized chunks and their completed High watermarks
     */
    public MySqlBinlogSplitFilter(Map<TableId, TableSchema> schemas, List<FinishedSnapshotSplitInfo> finished) {
        Objects.requireNonNull(schemas, "schemas");
        Objects.requireNonNull(finished, "finished");
        if (schemas.isEmpty() || finished.isEmpty()) {
            throw new IllegalArgumentException("Binlog filtering requires completed snapshot metadata");
        }
        for (Map.Entry<TableId, TableSchema> entry : schemas.entrySet()) {
            TableSchema schema = entry.getValue();
            if (schema.primaryKeys().size() != 1) {
                throw new IllegalArgumentException("Binlog filtering requires exactly one primary key");
            }
            String primaryKey = schema.primaryKeys().getFirst();
            int index = -1;
            for (int i = 0; i < schema.columnCount(); i++) {
                if (schema.column(i).name().equals(primaryKey)) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                throw new IllegalArgumentException("Binlog primary key is not present in source schema");
            }
            completed.put(entry.getKey(), new ArrayList<>());
            keyPositions.put(entry.getKey(), index);
        }
        for (FinishedSnapshotSplitInfo chunk : finished) {
            List<FinishedSnapshotSplitInfo> table = completed.get(chunk.tableId());
            if (table == null) {
                throw new IllegalArgumentException("Unknown completed snapshot table");
            }
            for (FinishedSnapshotSplitInfo existing : table) {
                if (existing.splitId().equals(chunk.splitId())) {
                    throw new IllegalArgumentException("Duplicate completed snapshot chunk");
                }
            }
            table.add(chunk);
        }
        completed.replaceAll((table, infos) -> List.copyOf(infos));
    }

    /**
     * Emits only events after the matching chunk's High watermark.
     *
     * <p>Empty results still carry their Binlog offset and may advance checkpoint progress.
     * Callers must use this only after every chunk finished with bounded backfill.
     */
    public BinlogEvent filter(BinlogEvent event) {
        Objects.requireNonNull(event, "event");
        List<TableRecord> rows = event.records();
        if (rows.isEmpty()) {
            return event;
        }
        if (rows.size() == 2) {
            TableRecord before = rows.get(0);
            TableRecord after = rows.get(1);
            if (before.rowKind() != RowKind.UPDATE_BEFORE
                    || after.rowKind() != RowKind.UPDATE_AFTER
                    || !before.tableId().equals(after.tableId())) {
                throw new IllegalArgumentException("Binlog filter requires complete adjacent UPDATE pairs");
            }
            boolean keepBefore = shouldEmit(before, event);
            boolean keepAfter = shouldEmit(after, event);
            if (keepBefore && keepAfter) {
                return event;
            }
            if (keepBefore) {
                return new BinlogEvent(
                        List.of(new TableRecord(before.tableId(), RowKind.DELETE, before.row())), event.offset());
            }
            if (keepAfter) {
                return new BinlogEvent(
                        List.of(new TableRecord(after.tableId(), RowKind.INSERT, after.row())), event.offset());
            }
            return new BinlogEvent(List.of(), event.offset());
        }
        if (rows.size() != 1
                || rows.getFirst().rowKind() == RowKind.UPDATE_BEFORE
                || rows.getFirst().rowKind() == RowKind.UPDATE_AFTER) {
            throw new IllegalArgumentException("Binlog filter requires atomic change events");
        }
        return shouldEmit(rows.getFirst(), event) ? event : new BinlogEvent(List.of(), event.offset());
    }

    private boolean shouldEmit(TableRecord record, BinlogEvent event) {
        List<FinishedSnapshotSplitInfo> infos = completed.get(record.tableId());
        Integer position = keyPositions.get(record.tableId());
        if (infos == null || position == null) {
            throw new IllegalArgumentException("Binlog event references an unregistered source table");
        }
        long key = record.row().getLong(position);
        FinishedSnapshotSplitInfo match = null;
        for (FinishedSnapshotSplitInfo info : infos) {
            if (info.contains(key)) {
                if (match != null) {
                    throw new IllegalStateException("Overlapping finished snapshot key ranges");
                }
                match = info;
            }
        }
        if (match == null) {
            throw new IllegalStateException("Missing finished snapshot metadata for Binlog key");
        }
        return BinlogOffsetOrder.isAfter(event.offset(), match.highWatermark());
    }
}
