from pathlib import Path
C = Path('yak-flow/yak-flow-connector-cdc-mysql/src/main/java/io/yak/ops/connector/cdc/mysql/source')
def put(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.strip() + '\n')
def edit(path, old, new):
    text = path.read_text()
    if old not in text: raise AssertionError(f'Missing patch anchor in {path}: {old[:100]}')
    path.write_text(text.replace(old, new))

put(C/'split/MySqlSnapshotOutput.java', '''
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

    public BinlogOffset low() { return low; }
    public BinlogOffset high() { return high; }
    public List<TableRecord> rows() { return rows; }

    @Override
    public boolean equals(Object other) {
        return other instanceof MySqlSnapshotOutput output
                && low.equals(output.low) && high.equals(output.high) && rows.equals(output.rows);
    }

    @Override
    public int hashCode() { return Objects.hash(low, high, rows); }

    @Override
    public String toString() { return "MySqlSnapshotOutput{rows=" + rows.size() + ", values=redacted}"; }

    /** Prevents downstream consumers from mutating the checkpoint's retained row objects. */
    private static final class SnapshotRow implements RowData {
        private final GenericRowData values;
        private SnapshotRow(RowData row) {
            List<Object> fields = new ArrayList<>(row.getArity());
            for (int i = 0; i < row.getArity(); i++) fields.add(row.getField(i));
            values = new GenericRowData(fields);
        }
        @Override public int getArity() { return values.getArity(); }
        @Override public boolean isNullAt(int position) { return values.isNullAt(position); }
        @Override public Object getField(int position) { return values.getField(position); }
        @Override public boolean equals(Object other) {
            return other instanceof SnapshotRow row && values.equals(row.values);
        }
        @Override public int hashCode() { return values.hashCode(); }
        @Override public String toString() { return "SnapshotRow{values=redacted}"; }
    }
}
''')

put(C/'split/MySqlSnapshotSplit.java', '''
package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.core.data.TableId;
import java.util.Objects;

/** Frozen key range, durable normalized output, and mailbox-owned output position for one chunk. */
public record MySqlSnapshotSplit(
        String splitId,
        String fingerprint,
        TableId tableId,
        Long lowerInclusive,
        Long upperExclusive,
        Long lastEmittedKey,
        MySqlSnapshotOutput output,
        int outputPosition,
        FinishedSnapshotSplitInfo finished) implements MySqlHybridSplit {

    /** Creates an unprepared range; numeric cursors are used only by the JDBC scan primitive. */
    public MySqlSnapshotSplit(String splitId, String fingerprint, TableId tableId,
            Long lowerInclusive, Long upperExclusive, Long lastEmittedKey) {
        this(splitId, fingerprint, tableId, lowerInclusive, upperExclusive, lastEmittedKey, null, 0, null);
    }

    public MySqlSnapshotSplit {
        if (splitId == null || splitId.isBlank() || fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("Snapshot split requires a stable ID and fingerprint");
        }
        Objects.requireNonNull(tableId, "tableId");
        if (lowerInclusive != null && upperExclusive != null && lowerInclusive >= upperExclusive) {
            throw new IllegalArgumentException("Snapshot split bounds must be strictly increasing");
        }
        if (lastEmittedKey != null && ((lowerInclusive != null && lastEmittedKey < lowerInclusive)
                || (upperExclusive != null && lastEmittedKey >= upperExclusive))) {
            throw new IllegalArgumentException("Snapshot split cursor is outside its range");
        }
        if (outputPosition < 0 || (output == null && outputPosition != 0)
                || (output != null && (finished != null || lastEmittedKey != null
                        || outputPosition > output.rows().size()))) {
            throw new IllegalArgumentException("Inconsistent normalized snapshot output state");
        }
        if (output != null && output.rows().stream().anyMatch(row -> !tableId.equals(row.tableId()))) {
            throw new IllegalArgumentException("Snapshot output crosses source tables");
        }
        if (finished != null && (!splitId.equals(finished.splitId()) || !tableId.equals(finished.tableId())
                || !Objects.equals(lowerInclusive, finished.lowerInclusive())
                || !Objects.equals(upperExclusive, finished.upperExclusive()) || lastEmittedKey != null)) {
            throw new IllegalArgumentException("Finished snapshot metadata differs from assigned range");
        }
    }

    public boolean prepared() { return output != null || finished != null; }

    public MySqlSnapshotSplit withOutput(MySqlSnapshotOutput normalized) {
        if (prepared() || lastEmittedKey != null) throw new IllegalStateException("Snapshot is already prepared or advanced");
        return new MySqlSnapshotSplit(splitId, fingerprint, tableId, lowerInclusive, upperExclusive,
                null, Objects.requireNonNull(normalized, "normalized"), 0, null);
    }

    /** Advances only after all rows in the emitted batch were accepted by the downstream collector. */
    public MySqlSnapshotSplit withOutputPosition(int position) {
        if (output == null || position < outputPosition || position > output.rows().size()
                || (position == outputPosition && !output.rows().isEmpty())) {
            throw new IllegalArgumentException("Invalid snapshot output progress");
        }
        if (position == output.rows().size()) {
            return new MySqlSnapshotSplit(splitId, fingerprint, tableId, lowerInclusive, upperExclusive, null,
                    null, 0, new FinishedSnapshotSplitInfo(tableId, splitId, lowerInclusive, upperExclusive, output.high()));
        }
        return new MySqlSnapshotSplit(splitId, fingerprint, tableId, lowerInclusive, upperExclusive,
                null, output, position, null);
    }

    public MySqlSnapshotSplit withLastEmittedKey(long key) {
        if (prepared() || (lastEmittedKey != null && key <= lastEmittedKey)) {
            throw new IllegalArgumentException("Snapshot key must advance before normalization only");
        }
        return new MySqlSnapshotSplit(splitId, fingerprint, tableId, lowerInclusive, upperExclusive, key);
    }

    @Override
    public String toString() {
        return "MySqlSnapshotSplit{id=" + splitId + ", prepared=" + prepared() + ", finished="
                + (finished != null) + ", values=redacted}";
    }
}
''')

put(C/'split/MySqlSnapshotOutputSerializer.java', '''
package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Scalar-only snapshot state encoding; never enables Java or polymorphic object deserialization. */
final class MySqlSnapshotOutputSerializer {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private MySqlSnapshotOutputSerializer() {}

    static void write(DataOutputStream out, MySqlSnapshotOutput output) throws IOException {
        output.low().writeTo(out);
        output.high().writeTo(out);
        out.writeInt(output.rows().size());
        for (TableRecord record : output.rows()) {
            int arity = record.row().getArity();
            if (arity > 4096) throw new IOException("Snapshot row arity exceeds state limit");
            out.writeInt(arity);
            for (int i = 0; i < arity; i++) writeValue(out, record.row().getField(i));
            if (out.size() > MAX_BYTES) throw new IOException("Snapshot output exceeds state byte limit");
        }
    }

    static MySqlSnapshotOutput read(DataInputStream in, TableId table) throws IOException {
        BinlogOffset low = BinlogOffset.readFrom(in);
        BinlogOffset high = BinlogOffset.readFrom(in);
        int count = count(in, 100_000);
        List<TableRecord> rows = new ArrayList<>(Math.min(count, 1024));
        for (int n = 0; n < count; n++) {
            int arity = count(in, 4096);
            GenericRowData row = new GenericRowData(arity);
            for (int i = 0; i < arity; i++) row.setField(i, readValue(in));
            rows.add(new TableRecord(table, RowKind.INSERT, row));
        }
        return new MySqlSnapshotOutput(low, high, rows);
    }

    private static void writeValue(DataOutputStream out, Object value) throws IOException {
        if (value == null) { out.writeByte(0); }
        else if (value instanceof Boolean v) { out.writeByte(1); out.writeBoolean(v); }
        else if (value instanceof Byte v) { out.writeByte(2); out.writeByte(v); }
        else if (value instanceof Short v) { out.writeByte(3); out.writeShort(v); }
        else if (value instanceof Integer v) { out.writeByte(4); out.writeInt(v); }
        else if (value instanceof Long v) { out.writeByte(5); out.writeLong(v); }
        else if (value instanceof Float v) { out.writeByte(6); out.writeFloat(v); }
        else if (value instanceof Double v) { out.writeByte(7); out.writeDouble(v); }
        else if (value instanceof BigDecimal v) { out.writeByte(8); text(out, v.toString()); }
        else if (value instanceof String v) { out.writeByte(9); text(out, v); }
        else if (value instanceof byte[] v) { out.writeByte(10); bytes(out, v); }
        else if (value instanceof LocalDate v) { out.writeByte(11); text(out, v.toString()); }
        else if (value instanceof LocalTime v) { out.writeByte(12); text(out, v.toString()); }
        else if (value instanceof LocalDateTime v) { out.writeByte(13); text(out, v.toString()); }
        else if (value instanceof OffsetDateTime v) { out.writeByte(14); text(out, v.toString()); }
        else throw new IOException("Unsupported normalized snapshot scalar type");
    }

    private static Object readValue(DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 0 -> null;
            case 1 -> in.readBoolean();
            case 2 -> in.readByte();
            case 3 -> in.readShort();
            case 4 -> in.readInt();
            case 5 -> in.readLong();
            case 6 -> in.readFloat();
            case 7 -> in.readDouble();
            case 8 -> new BigDecimal(text(in));
            case 9 -> text(in);
            case 10 -> bytes(in);
            case 11 -> LocalDate.parse(text(in));
            case 12 -> LocalTime.parse(text(in));
            case 13 -> LocalDateTime.parse(text(in));
            case 14 -> OffsetDateTime.parse(text(in));
            default -> throw new IOException("Unknown normalized snapshot scalar marker");
        };
    }

    private static int count(DataInputStream in, int max) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > max || count > in.available()) throw new IOException("Invalid snapshot state length");
        return count;
    }
    private static void text(DataOutputStream out, String value) throws IOException {
        if (value.length() > MAX_BYTES) throw new IOException("Snapshot string exceeds state limit");
        bytes(out, value.getBytes(StandardCharsets.UTF_8));
    }
    private static String text(DataInputStream in) throws IOException {
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes(in))).toString();
    }
    private static void bytes(DataOutputStream out, byte[] value) throws IOException {
        if (value.length > MAX_BYTES) throw new IOException("Snapshot scalar exceeds state limit");
        out.writeInt(value.length);
        out.write(value);
    }
    private static byte[] bytes(DataInputStream in) throws IOException {
        return in.readNBytes(count(in, MAX_BYTES));
    }
}
''')

# Preserve existing binlog value semantics while carrying the complete filtering contract.
p = C/'split/MySqlHybridBinlogSplit.java'
edit(p, 'import java.util.Objects;', 'import java.util.Objects;\nimport java.util.List;\nimport io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffsetOrder;')
edit(p, 'private final BinlogOffset highWatermark;', 'private final BinlogOffset highWatermark;\n    private final List<FinishedSnapshotSplitInfo> finishedSplits;')
edit(p, 'this.binlog = Objects.requireNonNull(binlog, "binlog");', '''this(binlog, phase, highWatermark, List.of());
    }

    public MySqlHybridBinlogSplit(MySqlBinlogSplit binlog, Phase phase, BinlogOffset highWatermark,
            List<FinishedSnapshotSplitInfo> finishedSplits) {
        this.finishedSplits = List.copyOf(Objects.requireNonNull(finishedSplits, "finishedSplits"));
        this.binlog = Objects.requireNonNull(binlog, "binlog");''')
edit(p, '/** Returns the underlying Binlog split. */', '''public List<FinishedSnapshotSplitInfo> finishedSplits() { return finishedSplits; }

    /** Returns the underlying Binlog split. */''')
edit(p, 'public MySqlHybridBinlogSplit startStreaming(BinlogOffset high) {', 'public MySqlHybridBinlogSplit startStreaming(BinlogOffset high, List<FinishedSnapshotSplitInfo> completed) {')
edit(p, 'return new MySqlHybridBinlogSplit(binlog, Phase.STREAMING, Objects.requireNonNull(high, "high"));', '''if (completed.isEmpty()) throw new IllegalArgumentException("Snapshot handoff requires per-chunk watermarks");
        BinlogOffset start = completed.stream().map(FinishedSnapshotSplitInfo::highWatermark)
                .min(BinlogOffsetOrder::compare).orElseThrow();
        return new MySqlHybridBinlogSplit(binlog.withProgress(start, binlog.schemaHistory()),
                Phase.STREAMING, Objects.requireNonNull(high, "high"), completed);''')
edit(p, 'new MySqlHybridBinlogSplit(binlog.withProgress(offset, history), phase, highWatermark)', 'new MySqlHybridBinlogSplit(binlog.withProgress(offset, history), phase, highWatermark, finishedSplits)')

# Version 1 lacks both the frozen output and filtering watermarks: explicitly refuse it.
p = C/'split/MySqlHybridSplitSerializer.java'
edit(p, 'private static final int VERSION = 1;', 'private static final int VERSION = 2;')
edit(p, 'writeLong(out, snapshot.lastEmittedKey());', '''writeLong(out, snapshot.lastEmittedKey());
                out.writeByte(snapshot.finished() != null ? 2 : snapshot.output() != null ? 1 : 0);
                if (snapshot.output() != null) {
                    MySqlSnapshotOutputSerializer.write(out, snapshot.output());
                    out.writeInt(snapshot.outputPosition());
                } else if (snapshot.finished() != null) {
                    snapshot.finished().highWatermark().writeTo(out);
                }''')
edit(p, 'out.write(encoded);', '''out.write(encoded);
                writeFinishedSplits(out, binlog.finishedSplits());''')
old = '''case 1 ->
                            new MySqlSnapshotSplit(
                                    in.readUTF(),
                                    in.readUTF(),
                                    new TableId(in.readUTF(), null, in.readUTF()),
                                    readLong(in),
                                    readLong(in),
                                    readLong(in));'''
new = '''case 1 -> {
                            String id = in.readUTF();
                            String fingerprint = in.readUTF();
                            TableId table = new TableId(in.readUTF(), null, in.readUTF());
                            Long lower = readLong(in), upper = readLong(in), key = readLong(in);
                            int stage = in.readUnsignedByte();
                            MySqlSnapshotOutput output = null;
                            FinishedSnapshotSplitInfo finished = null;
                            int position = 0;
                            if (stage == 1) {
                                output = MySqlSnapshotOutputSerializer.read(in, table);
                                position = in.readInt();
                            } else if (stage == 2) {
                                finished = new FinishedSnapshotSplitInfo(table, id, lower, upper, BinlogOffset.readFrom(in));
                            } else if (stage != 0) throw new IOException("Unknown snapshot output stage");
                            yield new MySqlSnapshotSplit(id, fingerprint, table, lower, upper, key, output, position, finished);
                        }'''
edit(p, old, new)
edit(p, '''MySqlHybridBinlogSplit.Phase.values()[phase],
                                    high);''', '''MySqlHybridBinlogSplit.Phase.values()[phase],
                                    high, readFinishedSplits(in));''')
edit(p, 'throw new IOException("Unsupported MySQL hybrid split version or size");', 'throw new IOException("Unsupported MySQL hybrid split state; version 1 requires explicit reinitialization");')
edit(p, 'private static void writeLong(DataOutputStream out, Long value)', '''public static void writeFinishedSplits(DataOutputStream out,
            java.util.List<FinishedSnapshotSplitInfo> finished) throws IOException {
        if (finished.size() > 10_000) throw new IOException("Too many finished snapshot chunks");
        out.writeInt(finished.size());
        for (FinishedSnapshotSplitInfo info : finished) {
            out.writeUTF(info.tableId().catalog());
            out.writeUTF(info.tableId().table());
            out.writeUTF(info.splitId());
            writeLong(out, info.lowerInclusive());
            writeLong(out, info.upperExclusive());
            info.highWatermark().writeTo(out);
        }
    }

    public static java.util.List<FinishedSnapshotSplitInfo> readFinishedSplits(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > 10_000 || size > in.available()) throw new IOException("Invalid finished snapshot count");
        var result = new java.util.ArrayList<FinishedSnapshotSplitInfo>(size);
        var ids = new java.util.HashSet<String>();
        for (int i = 0; i < size; i++) {
            TableId table = new TableId(in.readUTF(), null, in.readUTF());
            String id = in.readUTF();
            if (!ids.add(id)) throw new IOException("Duplicate finished snapshot identity");
            result.add(new FinishedSnapshotSplitInfo(table, id, readLong(in), readLong(in), BinlogOffset.readFrom(in)));
        }
        return java.util.List.copyOf(result);
    }

    private static void writeLong(DataOutputStream out, Long value)''')

put(C/'reader/MySqlHybridFetchedRecord.java', '''
package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.TableRecord;
import java.util.List;
import java.util.Objects;

/** One mailbox step: a prepared chunk, bounded output batch, complete Binlog change, or bootstrap anchor. */
public record MySqlHybridFetchedRecord(List<TableRecord> rows, Long snapshotKey,
        BinlogOffset binlogOffset, boolean lowWatermark,
        MySqlSnapshotSplit preparedSnapshot, Integer snapshotRowsEmitted) {

    public MySqlHybridFetchedRecord(List<TableRecord> rows, Long snapshotKey, BinlogOffset binlogOffset,
            boolean lowWatermark) {
        this(rows, snapshotKey, binlogOffset, lowWatermark, null, null);
    }

    public MySqlHybridFetchedRecord {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        int kinds = (snapshotKey == null ? 0 : 1) + (binlogOffset == null ? 0 : 1)
                + (preparedSnapshot == null ? 0 : 1) + (snapshotRowsEmitted == null ? 0 : 1);
        if (kinds != 1 || (snapshotRowsEmitted != null && snapshotRowsEmitted < 0)
                || (preparedSnapshot != null && (preparedSnapshot.output() == null || !rows.isEmpty()))
                || (lowWatermark && (binlogOffset == null || !rows.isEmpty()))) {
            throw new IllegalArgumentException("Inconsistent Hybrid fetched record kind");
        }
    }

    public static MySqlHybridFetchedRecord prepared(MySqlSnapshotSplit split) {
        return new MySqlHybridFetchedRecord(List.of(), null, null, false, split, null);
    }

    public static MySqlHybridFetchedRecord snapshotRows(List<TableRecord> rows, int position) {
        return new MySqlHybridFetchedRecord(rows, null, null, false, null, position);
    }

    @Override public String toString() {
        return "MySqlHybridFetchedRecord{rowCount=" + rows.size() + ", values=redacted}";
    }
}
''')

p = C/'reader/MySqlHybridSplitState.java'
edit(p, 'private BinlogOffset emittedOffset;', '''private BinlogOffset emittedOffset;
    private Long outputCheckpoint;
    private boolean outputDurable;''')
edit(p, 'snapshot = range;', 'snapshot = range;\n            outputDurable = range.prepared();')
edit(p, 'public void resume(BinlogOffset high) {', '''public void prepareSnapshot(MySqlSnapshotSplit prepared) {
        if (snapshot == null || snapshot.prepared() || !snapshot.splitId().equals(prepared.splitId())
                || !snapshot.tableId().equals(prepared.tableId())
                || !snapshot.fingerprint().equals(prepared.fingerprint())
                || !Objects.equals(snapshot.lowerInclusive(), prepared.lowerInclusive())
                || !Objects.equals(snapshot.upperExclusive(), prepared.upperExclusive())) {
            throw new IllegalStateException("Prepared snapshot differs from its assigned range");
        }
        snapshot = prepared;
        outputDurable = false;
    }

    public void snapshotCheckpoint(long checkpointId) {
        if (snapshot != null && snapshot.output() != null && !outputDurable && outputCheckpoint == null) {
            outputCheckpoint = checkpointId;
        }
    }

    public String completeCheckpoint(long checkpointId) {
        if (outputCheckpoint != null && checkpointId >= outputCheckpoint) {
            outputDurable = true;
            outputCheckpoint = null;
            return snapshot.splitId();
        }
        return null;
    }

    public void emitSnapshotRows(int position) {
        if (!outputDurable) throw new IllegalStateException("Snapshot output has no completed preparation checkpoint");
        snapshot = snapshot.withOutputPosition(position);
    }

    public void resume(BinlogOffset high,
            java.util.List<io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo> finished) {''')
edit(p, 'binlog = binlog.startStreaming(high);', 'binlog = binlog.startStreaming(high, finished);\n        emittedOffset = binlog.binlog().offset();')
edit(p, 'throw new IllegalStateException("Cannot checkpoint MySQL hybrid source before its low watermark");', 'return binlog;')

put(C/'events/MySqlResumeBinlogEvent.java', '''
package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.core.api.connector.source.SourceEvent;
import java.util.List;
import java.util.Objects;

/** Releases the checkpoint-protected Binlog handoff with its complete per-chunk filter metadata. */
public record MySqlResumeBinlogEvent(BinlogOffset highWatermark, List<FinishedSnapshotSplitInfo> finishedSplits)
        implements SourceEvent {
    public MySqlResumeBinlogEvent {
        Objects.requireNonNull(highWatermark, "highWatermark");
        finishedSplits = List.copyOf(Objects.requireNonNull(finishedSplits, "finishedSplits"));
        if (finishedSplits.isEmpty()) throw new IllegalArgumentException("Binlog handoff requires finished chunk metadata");
    }
}
''')
put(C/'events/MySqlSnapshotFinishedEvent.java', '''
package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.core.api.connector.source.SourceEvent;
import java.util.Objects;

/** Reports the normalized chunk's range and High only after all its output has been emitted. */
public record MySqlSnapshotFinishedEvent(FinishedSnapshotSplitInfo finished) implements SourceEvent {
    public MySqlSnapshotFinishedEvent { Objects.requireNonNull(finished, "finished"); }
    public String splitId() { return finished.splitId(); }
}
''')

p = C/'reader/MySqlHybridRecordEmitter.java'
edit(p, 'for (TableRecord row : event.rows()) {', '''if (event.preparedSnapshot() != null) {
            state.prepareSnapshot(event.preparedSnapshot());
            return;
        }
        for (TableRecord row : event.rows()) {''')
edit(p, 'if (event.snapshotKey() != null) {', '''if (event.snapshotRowsEmitted() != null) {
            state.emitSnapshotRows(event.snapshotRowsEmitted());
        } else if (event.snapshotKey() != null) {''')

p = C/'reader/MySqlHybridSourceReader.java'
edit(p, 'private io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset resumeHigh;', '''private MySqlResumeBinlogEvent resumeEvent;
    private final java.util.Set<String> acknowledged = new java.util.HashSet<>();
    private final Map<String, Long> finishedCheckpoint = new LinkedHashMap<>();''')
edit(p, 'this(new MySqlHybridSplitReader(config, metrics), context, metrics);', 'this(new MySqlHybridSplitReader(config, metrics, context.getIndexOfSubtask()), context, metrics);')
edit(p, '''if (resumeHigh != null && split instanceof MySqlHybridBinlogSplit) {
            state.resume(resumeHigh);''', '''if (resumeEvent != null && split instanceof MySqlHybridBinlogSplit) {
            state.resume(resumeEvent.highWatermark(), resumeEvent.finishedSplits());''')
edit(p, 'new MySqlSnapshotFinishedEvent(snapshot.splitId())', 'new MySqlSnapshotFinishedEvent(Objects.requireNonNull(snapshot.finished(), "Finished chunk has no watermark"))')
edit(p, 'import java.util.Map;', 'import java.util.Map;\nimport java.util.Objects;')
edit(p, 'List<MySqlHybridSplit> active = new ArrayList<>(super.snapshotState(checkpointId));', '''forEachActiveSplitState(state -> state.snapshotCheckpoint(checkpointId));
        acknowledged.forEach(id -> finishedCheckpoint.putIfAbsent(id, checkpointId));
        List<MySqlHybridSplit> active = new ArrayList<>(super.snapshotState(checkpointId));''')
edit(p, 'finishedUnacked.remove(ack.splitId());', 'if (finishedUnacked.containsKey(ack.splitId())) acknowledged.add(ack.splitId());')
edit(p, 'resumeHigh = resume.highWatermark();', 'resumeEvent = resume;')
edit(p, 'state.resume(resumeHigh);', 'state.resume(resume.highWatermark(), resume.finishedSplits());')
edit(p, 'splits.resumeBinlog(resumeHigh);', 'splits.resumeBinlog(resume);')
edit(p, '    @Override\n    public void handleSourceEvents(SourceEvent event) {', '''    @Override
    public void notifyCheckpointComplete(long checkpointId) {
        forEachActiveSplitState(state -> {
            String id = state.completeCheckpoint(checkpointId);
            if (id != null) splits.releaseSnapshot(id);
        });
        finishedCheckpoint.entrySet().removeIf(entry -> {
            if (entry.getValue() > checkpointId) return false;
            finishedUnacked.remove(entry.getKey());
            acknowledged.remove(entry.getKey());
            return true;
        });
    }

    @Override
    public void handleSourceEvents(SourceEvent event) {''')
