package io.yak.ops.core.event;

import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.data.TableId;
import java.util.Map;
import java.util.Objects;

/**
 * An indivisible table mutation with before and after images.
 *
 * <p>Rows follow the source's ownership-transfer contract. Operators that retain a row must
 * detach mutable fields. Diagnostic output deliberately excludes row values and metadata.
 */
public final class DataChangeEvent implements ChangeEvent {
    private final TableId tableId;
    private final RowData before;
    private final RowData after;
    private final OperationType op;
    private final Map<String, String> meta;
    private final ChangeEventId changeEventId;

    private DataChangeEvent(
            TableId tableId,
            RowData before,
            RowData after,
            OperationType op,
            Map<String, String> meta,
            ChangeEventId changeEventId) {
        this.tableId = Objects.requireNonNull(tableId, "tableId");
        this.op = Objects.requireNonNull(op, "op");
        if ((op == OperationType.INSERT || op == OperationType.REPLACE) && (before != null || after == null)
                || op == OperationType.DELETE && (before == null || after != null)
                || op == OperationType.UPDATE && (before == null || after == null)) {
            throw new IllegalArgumentException("Data change images do not match the operation");
        }
        this.before = before;
        this.after = after;
        this.meta = Map.copyOf(Objects.requireNonNull(meta, "meta"));
        this.changeEventId = changeEventId;
    }

    public static DataChangeEvent insertEvent(TableId tableId, RowData after) {
        return insertEvent(tableId, after, Map.of());
    }

    public static DataChangeEvent insertEvent(TableId tableId, RowData after, Map<String, String> meta) {
        return new DataChangeEvent(tableId, null, after, OperationType.INSERT, meta, null);
    }

    public static DataChangeEvent deleteEvent(TableId tableId, RowData before) {
        return deleteEvent(tableId, before, Map.of());
    }

    public static DataChangeEvent deleteEvent(TableId tableId, RowData before, Map<String, String> meta) {
        return new DataChangeEvent(tableId, before, null, OperationType.DELETE, meta, null);
    }

    public static DataChangeEvent updateEvent(TableId tableId, RowData before, RowData after) {
        return updateEvent(tableId, before, after, Map.of());
    }

    public static DataChangeEvent updateEvent(
            TableId tableId, RowData before, RowData after, Map<String, String> meta) {
        return new DataChangeEvent(tableId, before, after, OperationType.UPDATE, meta, null);
    }

    public static DataChangeEvent replaceEvent(TableId tableId, RowData after) {
        return replaceEvent(tableId, after, Map.of());
    }

    public static DataChangeEvent replaceEvent(TableId tableId, RowData after, Map<String, String> meta) {
        return new DataChangeEvent(tableId, null, after, OperationType.REPLACE, meta, null);
    }

    /** Preserves source identity when routing a mutation to a different table. */
    public static DataChangeEvent route(DataChangeEvent event, TableId tableId) {
        return new DataChangeEvent(tableId, event.before, event.after, event.op, event.meta, event.changeEventId);
    }

    /** Replaces both images while retaining source identity and metadata. */
    public DataChangeEvent withImages(RowData before, RowData after) {
        return new DataChangeEvent(tableId, before, after, op, meta, changeEventId);
    }

    /** Attaches the identity of the first image; UPDATE retains the two-image sequence. */
    public DataChangeEvent withChangeEventId(ChangeEventId identity) {
        if (changeEventId != null && !changeEventId.equals(identity)) {
            throw new IllegalStateException("Cannot reassign a CDC change identity");
        }
        if (identity != null
                && (identity.imageIndex() != 0 || identity.imageCount() != (op == OperationType.UPDATE ? 2 : 1))) {
            throw new IllegalArgumentException("Data change identity does not match the event images");
        }
        return new DataChangeEvent(tableId, before, after, op, meta, identity);
    }

    @Override
    public TableId tableId() {
        return tableId;
    }

    public RowData before() {
        return before;
    }

    public RowData after() {
        return after;
    }

    public OperationType op() {
        return op;
    }

    public Map<String, String> meta() {
        return meta;
    }

    public ChangeEventId changeEventId() {
        return changeEventId;
    }

    @Override
    public String toString() {
        return "DataChangeEvent{tableId=" + tableId + ", op=" + op + ", images=redacted}";
    }
}
