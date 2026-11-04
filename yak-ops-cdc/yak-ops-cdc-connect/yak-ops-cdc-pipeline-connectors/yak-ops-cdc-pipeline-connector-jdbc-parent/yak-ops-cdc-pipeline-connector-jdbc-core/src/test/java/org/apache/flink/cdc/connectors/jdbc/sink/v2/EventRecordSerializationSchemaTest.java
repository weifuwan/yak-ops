package org.apache.flink.cdc.connectors.jdbc.sink.v2;

import org.apache.flink.cdc.connectors.jdbc.internal.data.JdbcRowData;
import org.apache.flink.cdc.connectors.jdbc.internal.data.RowKind;
import org.apache.flink.cdc.common.data.DateData;
import org.apache.flink.cdc.common.data.DecimalData;
import org.apache.flink.cdc.common.data.LocalZonedTimestampData;
import org.apache.flink.cdc.common.data.TimeData;
import org.apache.flink.cdc.common.data.TimestampData;
import org.apache.flink.cdc.common.data.binary.BinaryRecordData;
import org.apache.flink.cdc.common.data.binary.BinaryStringData;
import org.apache.flink.cdc.common.event.CreateTableEvent;
import org.apache.flink.cdc.common.event.DataChangeEvent;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.common.types.DataTypes;
import org.apache.flink.cdc.runtime.typeutils.BinaryRecordDataGenerator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers the JDBC sink's conversion of real binary CDC records to schema-ordered Java values.
 * Adapted from the upstream JDBC pipeline connector test to verify typed values instead of JSON.
 */
class EventRecordSerializationSchemaTest {

    private static final TableId TABLE_ID = TableId.tableId("cdc_lab", "orders");

    private static final LocalDate ORDER_DATE = LocalDate.of(2024, 2, 29);
    private static final LocalTime ORDER_TIME = LocalTime.of(12, 34, 56, 789_000_000);
    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2024, 2, 29, 12, 34, 56, 123_456_000);
    private static final Instant EMITTED_AT = Instant.parse("2024-02-29T12:34:56.123456Z");
    private static final BigDecimal AMOUNT = new BigDecimal("12345678901234.5678");

    private static final Schema SCHEMA =
            Schema.newBuilder()
                    .physicalColumn("id", DataTypes.BIGINT().notNull())
                    .physicalColumn("enabled", DataTypes.BOOLEAN())
                    .physicalColumn("amount", DataTypes.DECIMAL(18, 4))
                    .physicalColumn("tiny_col", DataTypes.TINYINT())
                    .physicalColumn("small_col", DataTypes.SMALLINT())
                    .physicalColumn("int_col", DataTypes.INT())
                    .physicalColumn("big_col", DataTypes.BIGINT())
                    .physicalColumn("float_col", DataTypes.FLOAT())
                    .physicalColumn("double_col", DataTypes.DOUBLE())
                    .physicalColumn("customer", DataTypes.VARCHAR(64))
                    .physicalColumn("order_date", DataTypes.DATE())
                    .physicalColumn("order_time", DataTypes.TIME(3))
                    .physicalColumn("created_at", DataTypes.TIMESTAMP(6))
                    .physicalColumn("emitted_at", DataTypes.TIMESTAMP_LTZ(6))
                    .physicalColumn("binary_col", DataTypes.BINARY(3))
                    .physicalColumn("varbinary_col", DataTypes.VARBINARY(8))
                    .primaryKey("id")
                    .build();

    private static final BinaryRecordDataGenerator GENERATOR =
            new BinaryRecordDataGenerator(
                    SCHEMA.getColumnDataTypes().toArray(new DataType[0]));

    @Test
    void convertsBinaryRecordToTypedJdbcValues() throws Exception {
        EventRecordSerializationSchema serializer = initializedSerializer();

        JdbcRowData[] rows =
                serializer.serialize(DataChangeEvent.insertEvent(TABLE_ID, record(1L, "Alice")));

        assertEquals(1, rows.length);
        assertEquals(RowKind.INSERT, rows[0].getRowKind());
        assertEquals(TABLE_ID, rows[0].getTableId());
        assertEquals(SCHEMA, rows[0].getSchema());
        Object[] values = rows[0].getRows();
        assertEquals(16, values.length);
        assertEquals(1L, values[0]);
        assertEquals(true, values[1]);
        assertEquals(AMOUNT, values[2]);
        assertEquals((byte) 17, values[3]);
        assertEquals((short) 25566, values[4]);
        assertEquals(123456789, values[5]);
        assertEquals(1234567890123L, values[6]);
        assertEquals(1.25f, values[7]);
        assertEquals(2.5d, values[8]);
        assertEquals("Alice", values[9]);
        assertEquals(ORDER_DATE, values[10]);
        assertEquals(ORDER_TIME, values[11]);
        assertEquals(CREATED_AT, values[12]);
        assertEquals(EMITTED_AT, values[13]);
        assertArrayEquals(new byte[] {0, -1, 42}, (byte[]) values[14]);
        assertArrayEquals(new byte[] {7, 0, 127}, (byte[]) values[15]);
    }

    @Test
    void preservesNullValuesAndColumnPositions() throws Exception {
        EventRecordSerializationSchema serializer = initializedSerializer();
        Object[] fields = new Object[SCHEMA.getColumns().size()];
        fields[0] = 2L;

        JdbcRowData[] rows =
                serializer.serialize(
                        DataChangeEvent.insertEvent(TABLE_ID, GENERATOR.generate(fields)));

        assertEquals(1, rows.length);
        assertEquals(RowKind.INSERT, rows[0].getRowKind());
        assertEquals(2L, rows[0].getRows()[0]);
        for (int i = 1; i < fields.length; i++) {
            assertNull(rows[0].getRows()[i], "Expected NULL at column " + i);
        }
    }

    @Test
    void serializesUpdateAsDeleteThenInsertWithoutOverwritingBeforeValues() throws Exception {
        EventRecordSerializationSchema serializer = initializedSerializer();

        JdbcRowData[] rows =
                serializer.serialize(
                        DataChangeEvent.updateEvent(
                                TABLE_ID, record(3L, "before"), record(3L, "after")));

        assertEquals(2, rows.length);
        assertEquals(RowKind.DELETE, rows[0].getRowKind());
        assertEquals(RowKind.INSERT, rows[1].getRowKind());
        assertEquals("before", rows[0].getRows()[9]);
        assertEquals("after", rows[1].getRows()[9]);
        assertEquals(ORDER_DATE, rows[0].getRows()[10]);
        assertEquals(ORDER_DATE, rows[1].getRows()[10]);
    }

    @Test
    void serializesDeleteWithOriginalColumnValues() throws Exception {
        EventRecordSerializationSchema serializer = initializedSerializer();

        JdbcRowData[] rows =
                serializer.serialize(DataChangeEvent.deleteEvent(TABLE_ID, record(5L, "deleted")));

        assertEquals(1, rows.length);
        assertEquals(RowKind.DELETE, rows[0].getRowKind());
        assertEquals(5L, rows[0].getRows()[0]);
        assertEquals("deleted", rows[0].getRows()[9]);
        assertEquals(AMOUNT, rows[0].getRows()[2]);
    }

    @Test
    void createsSchemaMarkerWithoutRowPayload() throws Exception {
        EventRecordSerializationSchema serializer = new EventRecordSerializationSchema();

        JdbcRowData[] rows = serializer.serialize(new CreateTableEvent(TABLE_ID, SCHEMA));

        assertEquals(1, rows.length);
        assertEquals(RowKind.SCHEMA_CHANGE, rows[0].getRowKind());
        assertEquals(TABLE_ID, rows[0].getTableId());
        assertNull(rows[0].getRows());
        assertNull(rows[0].getSchema());
    }

    private static EventRecordSerializationSchema initializedSerializer() throws Exception {
        EventRecordSerializationSchema serializer = new EventRecordSerializationSchema();
        serializer.serialize(new CreateTableEvent(TABLE_ID, SCHEMA));
        return serializer;
    }

    private static BinaryRecordData record(long id, String customer) {
        return GENERATOR.generate(
                new Object[] {
                    id,
                    true,
                    DecimalData.fromBigDecimal(AMOUNT, 18, 4),
                    (byte) 17,
                    (short) 25566,
                    123456789,
                    1234567890123L,
                    1.25f,
                    2.5d,
                    BinaryStringData.fromString(customer),
                    DateData.fromLocalDate(ORDER_DATE),
                    TimeData.fromLocalTime(ORDER_TIME),
                    TimestampData.fromLocalDateTime(CREATED_AT),
                    LocalZonedTimestampData.fromInstant(EMITTED_AT),
                    new byte[] {0, -1, 42},
                    new byte[] {7, 0, 127}
                });
    }
}
