package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.TableSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Reconciles one bounded snapshot chunk with the ordered Binlog interval between its watermarks.
 *
 * <p>Like Flink CDC's snapshot buffering, INSERT and UPDATE_AFTER replace the row for a key,
 * while DELETE and UPDATE_BEFORE retract the old key. Both halves of a primary-key-changing
 * UPDATE are applied together. Rows are emitted only after the bounded backfill has ended.
 *
 * <p>This class has no JDBC, Debezium or Runtime dependencies. The owning snapshot task is
 * responsible for reading the complete Low-to-High interval; the buffer must never infer
 * completion from an idle Binlog stream. No partially merged rows may reach the mailbox.
 */
public final class MySqlChunkBackfill {

    private final MySqlSnapshotSplit split;
    private final int keyPosition;
    private final int columns;
    private final int maxRows;
    private final Map<Long, TableRecord> rows = new TreeMap<>();
    private boolean sealed;

    /**
     * Creates a bounded, restart-from-scratch chunk accumulator.
     *
     * @param split original half-open primary-key range
     * @param schema immutable source column order
     * @param maxRows maximum normalized rows retained in memory
     */
    public MySqlChunkBackfill(MySqlSnapshotSplit split, TableSchema schema, int maxRows) {
        this.split = Objects.requireNonNull(split, "split");
        Objects.requireNonNull(schema, "schema");
        if (maxRows < 1) {
            throw new IllegalArgumentException("Chunk backfill must have a positive row limit");
        }
        if (schema.primaryKeys().size() != 1) {
            throw new IllegalArgumentException("Chunk backfill requires one source primary key");
        }
        String key = schema.primaryKeys().getFirst();
        int position = -1;
        for (int index = 0; index < schema.columnCount(); index++) {
            if (schema.column(index).name().equals(key)) {
                position = index;
                break;
            }
        }
        if (position < 0) {
            throw new IllegalArgumentException("Chunk backfill primary key is not part of the source schema");
        }
        this.keyPosition = position;
        this.columns = schema.columnCount();
        this.maxRows = maxRows;
    }

    /** Buffers initial INSERT rows without exposing intermediate snapshot contents. */
    public void addSnapshot(TableRecord record) {
        ensureOpen();
        if (record.rowKind() != RowKind.INSERT) {
            throw new IllegalArgumentException("Snapshot chunk contains a non-insert record");
        }
        long key = key(record);
        if (!contains(key)) {
            throw new IllegalArgumentException("Snapshot row falls outside its chunk");
        }
        rows.put(key, snapshot(record));
        enforceCapacity();
    }

    /**
     * Applies a complete Debezium change; adjacent before/after images are indivisible.
     *
     * <p>Primary-key moves can cross chunk boundaries. The old key is removed from its
     * original chunk, and the new key is added only to the chunk containing the new value.
     */
    public void apply(BinlogEvent event) {
        ensureOpen();
        Objects.requireNonNull(event, "event");
        List<TableRecord> changes = event.records();
        if (changes.isEmpty()) {
            return;
        }
        if (changes.size() == 2) {
            TableRecord before = changes.get(0);
            TableRecord after = changes.get(1);
            if (before.rowKind() != RowKind.UPDATE_BEFORE || after.rowKind() != RowKind.UPDATE_AFTER) {
                throw new IllegalArgumentException("Backfill UPDATE images must be adjacent");
            }
            retract(before);
            upsert(after);
        } else if (changes.size() == 1) {
            TableRecord change = changes.getFirst();
            switch (change.rowKind()) {
                case INSERT -> upsert(change);
                case DELETE -> retract(change);
                case UPDATE_BEFORE, UPDATE_AFTER ->
                    throw new IllegalArgumentException("Backfill UPDATE requires both before and after");
            }
        } else {
            throw new IllegalArgumentException("Unsupported Binlog change group size");
        }
    }

    /**
     * Seals the normalized chunk after the Binlog reader proves it reached the High watermark.
     *
     * @return new INSERT records sorted by primary key, safe for downstream projection
     */
    public List<TableRecord> finish() {
        ensureOpen();
        sealed = true;
        return List.copyOf(new ArrayList<>(rows.values()));
    }

    private void retract(TableRecord record) {
        long key = key(record);
        if (contains(key)) {
            rows.remove(key);
        }
    }

    private void upsert(TableRecord record) {
        long key = key(record);
        if (contains(key)) {
            rows.put(key, snapshot(record));
            enforceCapacity();
        }
    }

    private TableRecord snapshot(TableRecord record) {
        RowData value = record.row();
        List<Object> fields = new ArrayList<>(columns);
        for (int index = 0; index < columns; index++) {
            fields.add(value.getField(index));
        }
        return new TableRecord(split.tableId(), RowKind.INSERT, new GenericRowData(fields));
    }

    private long key(TableRecord record) {
        Objects.requireNonNull(record, "record");
        if (!split.tableId().equals(record.tableId()) || record.row().getArity() != columns) {
            throw new IllegalArgumentException("Backfill event violates the frozen source table schema");
        }
        return record.row().getLong(keyPosition);
    }

    private boolean contains(long key) {
        return (split.lowerInclusive() == null || key >= split.lowerInclusive())
                && (split.upperExclusive() == null || key < split.upperExclusive());
    }

    private void enforceCapacity() {
        if (rows.size() > maxRows) {
            throw new IllegalStateException("Snapshot Backfill row limit exceeded; retry with smaller chunks");
        }
    }

    private void ensureOpen() {
        if (sealed) {
            throw new IllegalStateException("Snapshot Backfill has already been finalized");
        }
    }
}
