package org.apache.flink.cdc.connectors.jdbc.internal.converter;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataTypes;
import org.apache.flink.cdc.connectors.jdbc.internal.data.JdbcRowData;
import org.apache.flink.cdc.connectors.jdbc.internal.data.RichJdbcRowData;
import org.apache.flink.cdc.connectors.jdbc.internal.data.RowKind;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests schema-ordered parameter binding without a JDBC driver or a database. */
class DefaultJdbcRowConverterTest {
    private static final TableId TABLE_ID = TableId.tableId("db", "orders");

    @Test
    void bindsTypedNullsUsingSqlColumnTypesInStatementOrder() throws Exception {
        Schema schema =
                Schema.newBuilder()
                        .physicalColumn("id", DataTypes.BIGINT())
                        .physicalColumn("created_at", DataTypes.TIMESTAMP(3))
                        .physicalColumn("note", DataTypes.VARCHAR(60))
                        .physicalColumn("payload", DataTypes.VARBINARY(16))
                        .build();
        JdbcRowData row = row(schema, null, null, null, null);

        RecordingStatement recording = new RecordingStatement();
        new DefaultJdbcRowConverter()
                .createStatementBuilder(
                        schema,
                        List.of("payload", "created_at", "id", "note"),
                        schema.getColumnNames())
                .accept(recording.statement, row);

        assertEquals(List.of("setNull", "setNull", "setNull", "setNull"),
                recording.methods());
        assertEquals(List.of(Types.VARBINARY, Types.TIMESTAMP, Types.BIGINT, Types.VARCHAR),
                recording.values());
    }

    @Test
    void preservesJdbc42PrecisionAndExistingBinaryAndDecimalBinding() throws Exception {
        Schema schema = Schema.newBuilder()
                .physicalColumn("at", DataTypes.TIMESTAMP(3))
                .physicalColumn("cutoff", DataTypes.TIME(3))
                .physicalColumn("amount", DataTypes.DECIMAL(18, 4))
                .physicalColumn("bytes", DataTypes.VARBINARY(16))
                .build();
        LocalDateTime dateTime = LocalDateTime.of(2026, 11, 4, 12, 34, 56, 123_000_000);
        LocalTime time = LocalTime.of(10, 11, 12, 555_000_000);
        BigDecimal amount = new BigDecimal("1234.5678");
        byte[] binary = new byte[] {0, (byte) 255};
        JdbcRowData row = row(schema, dateTime, time, amount, binary);

        RecordingStatement recording = new RecordingStatement();
        new DefaultJdbcRowConverter()
                .createStatementBuilder(schema, schema.getColumnNames(), schema.getColumnNames())
                .accept(recording.statement, row);

        assertEquals(List.of("setObject", "setObject", "setBigDecimal", "setBytes"),
                recording.methods());
        assertEquals(dateTime, recording.values().get(0));
        assertEquals(time, recording.values().get(1));
        assertEquals(amount, recording.values().get(2));
        assertArrayEquals(binary, (byte[]) recording.values().get(3));
    }

    @Test
    void rejectsMissingSchemaColumnAndMismatchedRowWidth() {
        Schema schema = Schema.newBuilder()
                .physicalColumn("id", DataTypes.BIGINT())
                .physicalColumn("name", DataTypes.STRING())
                .build();
        DefaultJdbcRowConverter converter = new DefaultJdbcRowConverter();
        assertThrows(IllegalArgumentException.class,
                () -> converter.createStatementBuilder(schema, List.of("missing"),
                        schema.getColumnNames()));
        JdbcStatementBuilder<JdbcRowData> binder =
                converter.createStatementBuilder(schema, List.of("id"), schema.getColumnNames());
        RecordingStatement statement = new RecordingStatement();
        assertThrows(IllegalArgumentException.class,
                () -> binder.accept(statement.statement, row(schema, 1L)));
    }

    private static JdbcRowData row(Schema schema, Object... values) {
        return new RichJdbcRowData.Builder()
                .setTableId(TABLE_ID)
                .setSchema(schema)
                .setRowKind(RowKind.INSERT)
                .setRows(values)
                .build();
    }

    private static final class RecordingStatement {
        private final List<String> methodNames = new ArrayList<>();
        private final List<Object> parameters = new ArrayList<>();
        private final PreparedStatement statement =
                (PreparedStatement) Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {PreparedStatement.class},
                        (proxy, method, args) -> {
                            if (method.getName().startsWith("set") && args != null
                                    && args.length == 2 && args[0] instanceof Integer) {
                                methodNames.add(method.getName());
                                parameters.add(args[1]);
                                return null;
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });

        List<String> methods() {
            return methodNames;
        }

        List<Object> values() {
            return parameters;
        }
    }
}
