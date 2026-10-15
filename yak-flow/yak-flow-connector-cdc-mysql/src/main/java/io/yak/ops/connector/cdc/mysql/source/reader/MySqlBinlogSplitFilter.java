package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffsetOrder;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.TableSchema;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Filters Binlog changes already contained in per-chunk normalized snapshot output.
 *
 * <p>Each completed snapshot chunk contributes a High Watermark fence. A primary-key
 * move can have different fences for its old and new keys; if only one half survives,
 * convert that half to standalone
 * DELETE or INSERT so the downstream JDBC Sink never sees an incomplete UPDATE pair.
 */
public final class MySqlBinlogSplitFilter {

    private final Map<TableId, List<FinishedSnapshotSplitInfo>> completed = new LinkedHashMap<>();
    private final Map<TableId, Integer> keyPositions = new LinkedHashMap<>();
    private final Map<TableId, Integer> columnCounts = new LinkedHashMap<>();

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
            columnCounts.put(entry.getKey(), schema.columnCount());
        }
        Set<String> splitIds = new HashSet<>();
        for (FinishedSnapshotSplitInfo chunk : finished) {
            if (!splitIds.add(chunk.splitId())) {
                throw new IllegalArgumentException("Duplicate completed snapshot chunk identity");
            }
            List<FinishedSnapshotSplitInfo> table = completed.get(chunk.tableId());
            if (table == null) {
                throw new IllegalArgumentException("Unknown completed snapshot table");
            }
            table.add(chunk);
        }
        completed.replaceAll((table, infos) -> {
            if (infos.isEmpty()) {
                throw new IllegalArgumentException("Missing completed snapshot chunks for " + table);
            }
            infos.sort(Comparator.comparing(
                    FinishedSnapshotSplitInfo::lowerInclusive, Comparator.nullsFirst(Comparator.naturalOrder())));
            if (infos.getFirst().lowerInclusive() != null || infos.getLast().upperExclusive() != null) {
                throw new IllegalArgumentException("Finished snapshot ranges do not cover the entire keyspace");
            }
            for (int index = 1; index < infos.size(); index++) {
                if (infos.get(index - 1).upperExclusive() == null
                        || infos.get(index).lowerInclusive() == null
                        || !Objects.equals(
                                infos.get(index - 1).upperExclusive(),
                                infos.get(index).lowerInclusive())) {
                    throw new IllegalArgumentException("Finished snapshot ranges have a gap or overlap");
                }
            }
            return List.copyOf(infos);
        });
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
        if (record.row().getArity() != columnCounts.get(record.tableId())
                || record.row().isNullAt(position)) {
            throw new IllegalArgumentException("Binlog row violates its frozen schema or primary key");
        }
        long key = record.row().getLong(position);
        // Construction proves complete, non-overlapping coverage. Find the greatest
        // inclusive lower bound without scanning every chunk for each Binlog mutation.
        int left = 0;
        int right = infos.size() - 1;
        int matched = 0;
        while (left <= right) {
            int middle = left + (right - left) / 2;
            Long lower = infos.get(middle).lowerInclusive();
            if (lower == null || lower <= key) {
                matched = middle;
                left = middle + 1;
            } else {
                right = middle - 1;
            }
        }
        FinishedSnapshotSplitInfo match = infos.get(matched);
        if (!match.contains(key)) {
            throw new IllegalStateException("Missing finished snapshot metadata for Binlog key");
        }
        return BinlogOffsetOrder.isAfter(event.offset(), match.highWatermark());
    }
}
