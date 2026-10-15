package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffsetOrder;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable normalized chunk retained in a completed checkpoint before its first external write. */
public final class MySqlSnapshotOutput {
    private final BinlogOffset low;
    private final BinlogOffset high;
    private final List<TableRecord> rows;

    public MySqlSnapshotOutput(BinlogOffset low, BinlogOffset high, List<TableRecord> rows) {
        this.low = Objects.requireNonNull(low, "low");
        this.high = Objects.requireNonNull(high, "high");
        if (BinlogOffsetOrder.compare(low, high) > 0) {
            throw new IllegalArgumentException("Snapshot watermarks moved backwards");
        }
        if (Objects.requireNonNull(rows, "rows").size() > 100_000) {
            throw new IllegalArgumentException("Normalized snapshot row limit exceeded");
        }
        List<TableRecord> detached = new ArrayList<>(rows.size());
        for (TableRecord row : rows) {
            if (row.rowKind() != RowKind.INSERT) {
                throw new IllegalArgumentException("Normalized snapshot accepts INSERT rows only");
            }
            detached.add(new TableRecord(row.tableId(), RowKind.INSERT, new SnapshotRow(row.row())));
        }
        this.rows = List.copyOf(detached);
    }

    public BinlogOffset low() {
        return low;
    }

    public BinlogOffset high() {
        return high;
    }

    public List<TableRecord> rows() {
        return rows;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MySqlSnapshotOutput output)
                || !low.equals(output.low)
                || !high.equals(output.high)
                || rows.size() != output.rows.size()) return false;
        // TableRecord deliberately has execution identity, while this checkpoint is a value.
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
        int hash = Objects.hash(low, high);
        for (TableRecord row : rows) hash = 31 * hash + Objects.hash(row.tableId(), row.rowKind(), row.row());
        return hash;
    }

    @Override
    public String toString() {
        return "MySqlSnapshotOutput{rows=" + rows.size() + ", values=redacted}";
    }

    /** Prevents downstream consumers from mutating the checkpoint's retained row objects. */
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
