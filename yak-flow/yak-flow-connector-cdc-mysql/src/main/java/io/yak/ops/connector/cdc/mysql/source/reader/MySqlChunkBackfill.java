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
 * <p>INSERT and UPDATE_AFTER replace the row for a key,
 * while DELETE and UPDATE_BEFORE retract the old key. Both halves of a primary-key-changing
 * UPDATE are applied together. Rows are emitted only after the bounded backfill has ended.
 *
 * <p>This class has no JDBC, Debezium or Runtime dependencies. The owning snapshot task is
 * responsible for reading the complete Low-to-High interval; the buffer must never infer
 * completion from an idle Binlog stream. No partially merged rows may reach the mailbox.
 */
public final class MySqlChunkBackfill {

    private static final long DEFAULT_MAX_BYTES = 64L * 1024 * 1024;
    private final MySqlSnapshotSplit split;
    private final TableSchema schema;
    private final long maxBytes;
    private long retainedBytes;
    private boolean failed;
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
        this(split, schema, maxRows, DEFAULT_MAX_BYTES);
    }

    /**
     * Bounds retained rows and conservatively accounted payload bytes for one chunk.
     *
     * @param split frozen chunk identity and range
     * @param schema source column types used for variable-width payload accounting
     * @param maxRows maximum number of normalized rows
     * @param maxBytes retained payload budget, not an exact JVM heap measurement
     */
    public MySqlChunkBackfill(MySqlSnapshotSplit split, TableSchema schema, int maxRows, long maxBytes) {
        this.split = Objects.requireNonNull(split, "split");
        this.schema = Objects.requireNonNull(schema, "schema");
        if (maxBytes < 1) {
            throw new IllegalArgumentException("Chunk backfill must have a positive byte budget");
        }
        this.maxBytes = maxBytes;
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
        retain(key, record);
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
            // Validate both images before mutating the accumulator.
            key(before);
            key(after);
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
        List<TableRecord> result = List.copyOf(rows.values());
        rows.clear();
        retainedBytes = 0;
        return result;
    }

    private void retract(TableRecord record) {
        long key = key(record);
        if (contains(key)) {
            TableRecord removed = rows.remove(key);
            if (removed != null) retainedBytes -= payloadBytes(removed.row());
        }
    }

    private void upsert(TableRecord record) {
        long key = key(record);
        if (contains(key)) {
            retain(key, record);
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
        if (record.row().isNullAt(keyPosition)) {
            throw new IllegalArgumentException("Backfill primary key must not be null");
        }
        return record.row().getLong(keyPosition);
    }

    private boolean contains(long key) {
        return (split.lowerInclusive() == null || key >= split.lowerInclusive())
                && (split.upperExclusive() == null || key < split.upperExclusive());
    }

    private void retain(long key, TableRecord record) {
        TableRecord previous = rows.get(key);
        long size = payloadBytes(record.row());
        long nextBytes = retainedBytes - (previous == null ? 0 : payloadBytes(previous.row())) + size;
        if ((previous == null && rows.size() >= maxRows) || nextBytes > maxBytes) {
            failed = true;
            rows.clear();
            retainedBytes = 0;
            throw new IllegalStateException("Snapshot Backfill buffer limit exceeded; use smaller chunks");
        }
        rows.put(key, snapshot(record));
        retainedBytes = nextBytes;
    }

    private long payloadBytes(RowData row) {
        long bytes = 128L + 8L * columns;
        for (int index = 0; index < columns; index++) {
            if (row.isNullAt(index)) continue;
            bytes += switch (schema.column(index).dataType().getTypeRoot()) {
                case CHAR, VARCHAR -> 48L + 2L * row.getString(index).length();
                case BINARY, VARBINARY -> 24L + ((byte[]) row.getField(index)).length;
                default -> 64L;
            };
        }
        return bytes;
    }

    private void ensureOpen() {
        if (sealed || failed) {
            throw new IllegalStateException("Snapshot Backfill has already been finalized or failed");
        }
    }
}
