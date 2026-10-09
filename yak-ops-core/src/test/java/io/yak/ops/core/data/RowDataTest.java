package io.yak.ops.core.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.core.types.LogicalTypes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RowDataTest {

    @Test
    void fieldCountNullAccessAndInputListAreIndependent() {
        ArrayList<Object> mutable = new ArrayList<>(Arrays.asList(1L, null, "first"));
        GenericRowData row = new GenericRowData(mutable);
        mutable.set(2, "changed");
        assertEquals("first", row.getField(2));
        assertNull(row.getField(1));
        assertEquals(3, row.getArity());
        assertFalse(row.isNullAt(0));
        assertThrows(NullPointerException.class, () -> row.getString(1));
    }

    @Test
    void typedFieldGettersAcceptOnlyCanonicalInternalValues() {
        BigDecimal amount = new BigDecimal("10.25");
        LocalDate date = LocalDate.of(2026, 10, 9);
        LocalTime time = LocalTime.of(12, 34, 56, 123_000_000);
        LocalDateTime timestamp = date.atTime(time);
        OffsetDateTime zoned = timestamp.atOffset(java.time.ZoneOffset.ofHours(8));
        RowData row = GenericRowData.of(
                true, (byte) 2, (short) 3, 4, 5L, 6f, 7d, "hello", amount, date, time, timestamp, zoned);

        assertEquals(true, row.getBoolean(0));
        assertEquals((byte) 2, row.getByte(1));
        assertEquals((short) 3, row.getShort(2));
        assertEquals(4, row.getInt(3));
        assertEquals(5L, row.getLong(4));
        assertEquals(6f, row.getFloat(5));
        assertEquals(7d, row.getDouble(6));
        assertEquals("hello", row.getString(7));
        assertEquals(amount, row.getDecimal(8, 10, 2));
        assertEquals(date, row.getDate(9));
        assertEquals(time, row.getTime(10, 3));
        assertEquals(timestamp, row.getTimestamp(11, 3));
        assertEquals(zoned, row.getZonedTimestamp(12, 3));
        assertThrows(ClassCastException.class, () -> row.getInt(7));
        assertThrows(IllegalArgumentException.class, () -> row.getDecimal(8, 39, 2));
    }

    @Test
    void fieldGetterHandlesNullAndRejectsUnresolvedDecimal() {
        RowData row = GenericRowData.of(123L, "done", null, new BigDecimal("1.25"));
        assertEquals(123L, RowData.createFieldGetter(LogicalTypes.BIGINT, 0).getFieldOrNull(row));
        assertEquals("done", RowData.createFieldGetter(LogicalTypes.varchar(4), 1).getFieldOrNull(row));
        assertNull(RowData.createFieldGetter(LogicalTypes.STRING, 2).getFieldOrNull(row));
        assertEquals(new BigDecimal("1.25"), RowData.createFieldGetter(LogicalTypes.decimal(4, 2), 3)
                .getFieldOrNull(row));
        assertThrows(
                IllegalArgumentException.class,
                () -> RowData.createFieldGetter(LogicalTypes.decimal((Integer) null, null), 3));
    }

    @Test
    void binaryAndTimestampFieldsAreIsolatedFromMutatingCallers() {
        byte[] bytes = new byte[] {1, 2, 3};
        java.sql.Timestamp timestamp = java.sql.Timestamp.valueOf("2026-10-09 12:01:02");
        GenericRowData row = GenericRowData.of(bytes, timestamp);
        bytes[0] = 99;
        timestamp.setTime(0);
        assertArrayEquals(new byte[] {1, 2, 3}, row.getBinary(0));

        byte[] exposed = row.getBinary(0);
        exposed[1] = 99;
        assertArrayEquals(new byte[] {1, 2, 3}, row.getBinary(0));

        java.sql.Timestamp extracted = (java.sql.Timestamp) row.getField(1);
        extracted.setTime(0);
        assertEquals("2026-10-09 12:01:02.0", row.getField(1).toString());
        assertEquals(row.copy(), row);
    }

    @Test
    void tableRecordCarriesPhysicalIdentityAndChangeKind() {
        TableId table = new TableId("store", "public", "orders");
        RowData row = GenericRowData.of(1, "updated");
        TableRecord record = new TableRecord(table, RowKind.UPDATE_AFTER, row);
        assertEquals(table, record.tableId());
        assertEquals(RowKind.UPDATE_AFTER, record.rowKind());
        assertEquals(List.of(1, "updated"), List.of(record.row().getField(0), record.row().getField(1)));
    }
}
