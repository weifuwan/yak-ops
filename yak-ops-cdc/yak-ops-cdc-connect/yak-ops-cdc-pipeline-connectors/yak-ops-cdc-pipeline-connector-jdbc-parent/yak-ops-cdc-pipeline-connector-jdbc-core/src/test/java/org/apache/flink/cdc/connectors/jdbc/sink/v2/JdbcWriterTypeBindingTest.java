/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.cdc.connectors.jdbc.sink.v2;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataTypes;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies JDBC parameter types and primary-key binding without a database. */
class JdbcWriterTypeBindingTest {
    private static final TableId TABLE_ID = TableId.tableId("cdc_lab", "orders");

    @Test
    void bindsTypedParametersInSqlColumnOrder() throws Exception {
        LocalDate date = LocalDate.of(2024, 2, 29);
        LocalTime time = LocalTime.of(12, 34, 56, 789_000_000);
        LocalDateTime localTimestamp =
                LocalDateTime.of(2024, 2, 29, 12, 34, 56, 123_456_000);
        Instant instant = Instant.parse("2024-02-29T12:34:56.123456Z");
        ZonedDateTime zoned = instant.atZone(ZoneId.of("Asia/Shanghai"));
        BigDecimal amount = new BigDecimal("12345678901234.5678");
        byte[] bytes = new byte[] {0, -1, 42};

        List<String> rowColumns =
                List.of("name", "order_date", "order_time", "amount", "binary_col",
                        "emitted_at", "zoned_at", "created_at", "nullable_col");
        List<String> sqlColumns =
                List.of("order_date", "amount", "binary_col", "emitted_at", "zoned_at",
                        "order_time", "created_at", "name", "nullable_col");
        JdbcRowData row =
                new RichJdbcRowData.Builder()
                        .setTableId(TABLE_ID)
                        .setRowKind(RowKind.INSERT)
                        .setRows(new Object[] {
                            "Alice", date, time, amount, bytes, instant, zoned, localTimestamp, null
                        })
                        .build();

        RecordingStatement recording = new RecordingStatement();
        JdbcStatementBuilder<JdbcRowData> builder =
                JdbcWriter.getStatementBuilder(sqlColumns, rowColumns);
        builder.accept(recording.statement, row);

        assertEquals(9, recording.calls.size());
        assertEquals("setDate", recording.calls.get(1).method);
        assertEquals(Date.valueOf(date), recording.calls.get(1).value);
        assertEquals("setBigDecimal", recording.calls.get(2).method);
        assertEquals(amount, recording.calls.get(2).value);
        assertEquals("setBytes", recording.calls.get(3).method);
        assertArrayEquals(bytes, (byte[]) recording.calls.get(3).value);
        assertEquals("setTimestamp", recording.calls.get(4).method);
        assertEquals(Timestamp.from(instant), recording.calls.get(4).value);
        assertEquals("setTimestamp", recording.calls.get(5).method);
        assertEquals(Timestamp.from(zoned.toInstant()), recording.calls.get(5).value);
        assertEquals("setObject", recording.calls.get(6).method);
        assertEquals(time, recording.calls.get(6).value);
        assertEquals("setObject", recording.calls.get(7).method);
        assertEquals(localTimestamp, recording.calls.get(7).value);
        assertEquals("setObject", recording.calls.get(8).method);
        assertEquals("Alice", recording.calls.get(8).value);
        assertEquals("setObject", recording.calls.get(9).method);
        assertEquals(null, recording.calls.get(9).value);
    }

    @Test
    void compositePrimaryKeyUsesSqlOrderEvenWhenSchemaOrderDiffers() throws Exception {
        Schema schema =
                Schema.newBuilder()
                        .physicalColumn("secondary_id", DataTypes.BIGINT().notNull())
                        .physicalColumn("description", DataTypes.VARCHAR(64))
                        .physicalColumn("primary_id", DataTypes.VARCHAR(64).notNull())
                        .primaryKey("primary_id", "secondary_id")
                        .build();
        JdbcRowData row =
                new RichJdbcRowData.Builder()
                        .setRowKind(RowKind.DELETE)
                        .setTableId(TABLE_ID)
                        .setSchema(schema)
                        .setRows(new Object[] {42L, "not-a-key", "primary-key"})
                        .build();

        Function<JdbcRowData, JdbcRowData> keyExtractor =
                JdbcWriter.createRowKeyExtractor(TABLE_ID, schema);
        JdbcRowData key = keyExtractor.apply(row);
        assertEquals(RowKind.PK_ROW, key.getRowKind());
        assertArrayEquals(new Object[] {42L, "primary-key"}, key.getRows());
        assertEquals(List.of("secondary_id", "primary_id"), key.getSchema().getColumnNames());

        RecordingStatement recording = new RecordingStatement();
        JdbcWriter.getStatementBuilder(
                        schema.primaryKeys(), key.getSchema().getColumnNames())
                .accept(recording.statement, key);

        assertEquals(2, recording.calls.size());
        assertEquals("primary-key", recording.calls.get(1).value);
        assertEquals(42L, recording.calls.get(2).value);
    }

    @Test
    void binaryPrimaryKeysCompareByContentsAndAreDefensivelyCopied() {
        Schema schema =
                Schema.newBuilder()
                        .physicalColumn("binary_id", DataTypes.VARBINARY(8).notNull())
                        .primaryKey("binary_id")
                        .build();
        byte[] originalKey = new byte[] {3, 2, 1};
        Object[] input = new Object[] {originalKey};
        RichJdbcRowData first =
                new RichJdbcRowData.Builder()
                        .setRowKind(RowKind.PK_ROW)
                        .setTableId(TABLE_ID)
                        .setSchema(schema)
                        .setRows(input)
                        .build();
        RichJdbcRowData second =
                new RichJdbcRowData.Builder()
                        .setRowKind(RowKind.PK_ROW)
                        .setTableId(TABLE_ID)
                        .setSchema(schema)
                        .setRows(new Object[] {new byte[] {3, 2, 1}})
                        .build();

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        originalKey[0] = 99;
        input[0] = new byte[] {9, 9, 9};
        assertArrayEquals(new byte[] {3, 2, 1}, (byte[]) first.getRows()[0]);
        Object[] returned = first.getRows();
        ((byte[]) returned[0])[1] = 77;
        assertArrayEquals(new byte[] {3, 2, 1}, (byte[]) first.getRows()[0]);
        assertEquals(second, first);
    }

    @Test
    void rowStringDoesNotExposePayload() {
        RichJdbcRowData row =
                new RichJdbcRowData.Builder()
                        .setRowKind(RowKind.INSERT)
                        .setTableId(TABLE_ID)
                        .setRows(new Object[] {"private-customer-data"})
                        .build();

        assertFalse(row.toString().contains("private-customer-data"));
        assertTrue(row.toString().contains("columnCount=1"));
    }

    @Test
    void rejectsMismatchedColumnsAndRowWidths() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JdbcWriter.getStatementBuilder(
                        List.of("non_existent"), List.of("id")));

        JdbcStatementBuilder<JdbcRowData> builder =
                JdbcWriter.getStatementBuilder(List.of("id"), List.of("id", "name"));
        JdbcRowData row =
                new RichJdbcRowData.Builder()
                        .setRowKind(RowKind.INSERT)
                        .setTableId(TABLE_ID)
                        .setRows(new Object[] {1L})
                        .build();
        RecordingStatement recording = new RecordingStatement();

        assertThrows(IllegalArgumentException.class, () -> builder.accept(recording.statement, row));
    }

    private static final class RecordingStatement {
        private final Map<Integer, ParameterCall> calls = new HashMap<>();
        private final PreparedStatement statement =
                (PreparedStatement)
                        Proxy.newProxyInstance(
                                JdbcWriterTypeBindingTest.class.getClassLoader(),
                                new Class<?>[] {PreparedStatement.class},
                                (proxy, method, args) -> {
                                    if (method.getName().startsWith("set")
                                            && args != null
                                            && args.length == 2
                                            && args[0] instanceof Integer) {
                                        calls.put(
                                                (Integer) args[0],
                                                new ParameterCall(method.getName(), args[1]));
                                        return null;
                                    }
                                    throw new UnsupportedOperationException(
                                            "Unexpected PreparedStatement call: " + method.getName());
                                });
    }

    private static final class ParameterCall {
        private final String method;
        private final Object value;

        private ParameterCall(String method, Object value) {
            this.method = method;
            this.value = value;
        }
    }
}
