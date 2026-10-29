package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableRecord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Immutable transient normalized chunk produced by Low/High watermark backfill. */
public final class MySqlSnapshotOutput {
    private final DebeziumOffset low;
    private final DebeziumOffset high;
    private final List<TableRecord> rows;
    private final byte[] schemaHistory;

    public MySqlSnapshotOutput(DebeziumOffset low, DebeziumOffset high, List<TableRecord> rows) {
        this(low, high, rows, new byte[0]);
    }

    /**
     * Keeps the completed Debezium 2.7 Schema History from the same bounded backfill
     * stream that advanced to this chunk's high watermark. The eventual Binlog split
     * must use history valid at its starting offset, not a later schema-only snapshot.
     */
    public MySqlSnapshotOutput(DebeziumOffset low, DebeziumOffset high, List<TableRecord> rows, byte[] schemaHistory) {
        this.schemaHistory =
                Objects.requireNonNull(schemaHistory, "schemaHistory").clone();
        this.low = Objects.requireNonNull(low, "low");
        this.high = Objects.requireNonNull(high, "high");
        if (low.compareTo(high) > 0) {
            throw new IllegalArgumentException("Snapshot watermarks moved backwards");
        }
        Objects.requireNonNull(rows, "rows");
        if (rows instanceof MySqlSnapshotRows compact) {
            // Copy each distinct image once, without expanding millions of equal physical rows.
            this.rows = compact.mapRows(MySqlSnapshotOutput::detach);
        } else {
            if (rows.size() > 100_000) {
                throw new IllegalArgumentException("Normalized snapshot row limit exceeded");
            }
            List<TableRecord> detached = new ArrayList<>(rows.size());
            for (TableRecord row : rows) detached.add(detach(row));
            this.rows = List.copyOf(detached);
        }
    }

    private static TableRecord detach(TableRecord row) {
        if (row.rowKind() != RowKind.INSERT) {
            throw new IllegalArgumentException("Normalized snapshot accepts INSERT rows only");
        }
        return new TableRecord(row.tableId(), RowKind.INSERT, new SnapshotRow(row.row()));
    }

    public DebeziumOffset low() {
        return low;
    }

    public DebeziumOffset high() {
        return high;
    }

    public List<TableRecord> rows() {
        return rows;
    }

    public byte[] schemaHistory() {
        return schemaHistory.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MySqlSnapshotOutput output)
                || !low.equals(output.low)
                || !high.equals(output.high)
                || rows.size() != output.rows.size()
                || !Arrays.equals(schemaHistory, output.schemaHistory)) return false;
        // TableRecord deliberately has execution identity, while this in-memory snapshot image is a value.
        for (int i = 0; i < rows.size(); i++) {
            TableRecord left = rows.get(i), right = output.rows.get(i);
            if (!left.tableId().equals(right.tableId())
                    || left.rowKind() != right.rowKind()
                    || !left.row().equals(right.row())) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = Objects.hash(low, high, Arrays.hashCode(schemaHistory));
        for (TableRecord row : rows) hash = 31 * hash + Objects.hash(row.tableId(), row.rowKind(), row.row());
        return hash;
    }

    @Override
    public String toString() {
        return "MySqlSnapshotOutput{rows=" + rows.size() + ", values=redacted}";
    }

    /** Prevents downstream consumers from mutating normalized source rows while they are emitted. */
    private static final class SnapshotRow implements RowData {
        private final GenericRowData values;

        private SnapshotRow(RowData row) {
            List<Object> fields = new ArrayList<>(row.getArity());
            for (int i = 0; i < row.getArity(); i++) fields.add(row.getField(i));
            values = new GenericRowData(fields);
        }

        @Override
        public int getArity() {
            return values.getArity();
        }

        @Override
        public boolean isNullAt(int position) {
            return values.isNullAt(position);
        }

        @Override
        public Object getField(int position) {
            return values.getField(position);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SnapshotRow row && values.equals(row.values);
        }

        @Override
        public int hashCode() {
            return values.hashCode();
        }

        @Override
        public String toString() {
            return "SnapshotRow{values=redacted}";
        }
    }
}
