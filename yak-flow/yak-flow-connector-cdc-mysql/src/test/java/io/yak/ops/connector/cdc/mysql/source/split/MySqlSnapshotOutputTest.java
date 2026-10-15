package io.yak.ops.connector.cdc.mysql.source.split;

import static org.junit.jupiter.api.Assertions.*;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlHybridSplitState;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Exercises durable preparation, scalar state, and partial output without live database dependencies. */
class MySqlSnapshotOutputTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");

    @Test
    void preparationMustBeCoveredByCompletedCheckpointBeforeEmission() throws Exception {
        MySqlSnapshotSplit prepared = prepared();
        MySqlHybridSplitState state = new MySqlHybridSplitState(range());
        state.prepareSnapshot(prepared);
        assertThrows(IllegalStateException.class, () -> state.emitSnapshotRows(1));
        state.snapshotCheckpoint(10);
        state.snapshotCheckpoint(11);
        assertNull(state.completeCheckpoint(9));
        assertEquals("chunk", state.completeCheckpoint(10));
        state.emitSnapshotRows(1);
        MySqlSnapshotSplit partial = (MySqlSnapshotSplit) state.checkpoint(null);
        assertEquals(1, partial.outputPosition());
        assertEquals(2, partial.output().rows().size());
        assertEquals("original", partial.output().rows().getFirst().row().getString(1));
        MySqlHybridSplitState recovered = new MySqlHybridSplitState(roundTrip(partial));
        recovered.emitSnapshotRows(2);
        MySqlSnapshotSplit done = (MySqlSnapshotSplit) recovered.checkpoint(null);
        assertNull(done.output());
        assertEquals(offset(200), done.finished().highWatermark());
        assertEquals(done, roundTrip(done));
        assertNull(((MySqlSnapshotSplit) new MySqlHybridSplitState(roundTrip(done)).checkpoint(null)).output());
    }

    @Test
    void emptyChunkStillRequiresDurablePreparationAndRetainsItsHigh() {
        var prepared = range().withOutput(new MySqlSnapshotOutput(offset(100), offset(100), List.of()));
        var state = new MySqlHybridSplitState(range());
        state.prepareSnapshot(prepared);
        assertThrows(IllegalStateException.class, () -> state.emitSnapshotRows(0));
        state.snapshotCheckpoint(3);
        state.completeCheckpoint(3);
        state.emitSnapshotRows(0);
        assertEquals(offset(100), ((MySqlSnapshotSplit) state.checkpoint(null)).finished().highWatermark());
    }

    @Test
    void normalizedRowsCannotBeMutatedThroughProducerOrReaderReferences() {
        byte[] payload = {1,2,3};
        GenericRowData mutable = GenericRowData.of(1L, "original", payload);
        var result = new MySqlSnapshotOutput(offset(100), offset(200), List.of(new TableRecord(TABLE, RowKind.INSERT, mutable)));
        mutable.setField(1, "changed");
        payload[0] = 9;
        var row = result.rows().getFirst().row();
        assertFalse(row instanceof GenericRowData);
        assertEquals("original", row.getString(1));
        byte[] exposed = (byte[]) row.getField(2);
        exposed[0] = 8;
        assertArrayEquals(new byte[]{1,2,3}, (byte[]) row.getField(2));
        assertThrows(UnsupportedOperationException.class, () -> result.rows().clear());
        assertFalse(result.toString().contains("original"));
    }

    @Test
    void roundTripsAllCanonicalScalarFamiliesWithoutTypeCoercion() throws Exception {
        GenericRowData values = GenericRowData.of(1L, null, true, (byte) 2, (short) 3, 4, 5L, 1.25f, 2.5d,
                new BigDecimal("123456.78900"), "长字符串".repeat(24000), new byte[]{0,1,-1},
                LocalDate.of(2020,2,29), LocalTime.of(12,13,14,123456789),
                LocalDateTime.of(2020,2,29,12,13,14,123456789), OffsetDateTime.parse("2020-02-29T12:13:14.123456789+08:00"));
        var original = range().withOutput(new MySqlSnapshotOutput(offset(100), offset(200),
                List.of(new TableRecord(TABLE, RowKind.INSERT, values))));
        MySqlSnapshotSplit restored = roundTrip(original);
        assertEquals(original, restored);
        assertEquals(original.hashCode(), restored.hashCode());
    }

    @Test
    void rejectsOldVersionTruncationUnsupportedScalarsAndTrailingBytes() throws Exception {
        var serializer = new MySqlHybridSplitSerializer();
        byte[] bytes = serializer.serialize(prepared());
        assertThrows(IOException.class, () -> serializer.deserialize(1, bytes));
        assertThrows(IOException.class, () -> serializer.deserialize(serializer.getVersion(), Arrays.copyOf(bytes, bytes.length-1)));
        assertThrows(IOException.class, () -> serializer.deserialize(serializer.getVersion(), Arrays.copyOf(bytes, bytes.length+1)));
        var unsupported = range().withOutput(new MySqlSnapshotOutput(offset(100), offset(200),
                List.of(new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1L, new Object())))));
        assertThrows(IOException.class, () -> serializer.serialize(unsupported));
    }

    @Test
    void invalidOutputPositionsAndConflictingRangesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> prepared().withOutputPosition(3));
        var state = new MySqlHybridSplitState(range());
        var other = new MySqlSnapshotSplit("other", "fp", TABLE, null, null, null).withOutput(prepared().output());
        assertThrows(IllegalStateException.class, () -> state.prepareSnapshot(other));
        assertThrows(IllegalArgumentException.class, () -> new MySqlSnapshotOutput(offset(200), offset(100), List.of()));
    }

    private static MySqlSnapshotSplit prepared() {
        return range().withOutput(new MySqlSnapshotOutput(offset(100), offset(200), List.of(
                new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1L, "original")),
                new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(2L, "second")))));
    }
    private static MySqlSnapshotSplit range() { return new MySqlSnapshotSplit("chunk", "fp", TABLE, null, null, null); }
    private static BinlogOffset offset(long pos) { return new BinlogOffset(Map.of("server","shop"), Map.of("file","mysql-bin.000001","pos",pos)); }
    private static MySqlSnapshotSplit roundTrip(MySqlSnapshotSplit split) throws Exception {
        var serializer = new MySqlHybridSplitSerializer();
        return (MySqlSnapshotSplit) serializer.deserialize(serializer.getVersion(), serializer.serialize(split));
    }
}
