package io.yak.ops.flow.connector.cdc.mysql.debezium;

import io.yak.ops.flow.api.row.RowKind;
import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakDataType;
import io.yak.ops.flow.api.row.YakRow;
import io.yak.ops.flow.api.row.YakTableSchema;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;

/**
 * 将 Debezium MySQL Connect envelope 转换为 YakFlow RowKind changelog。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class DebeziumRecordConverter {

    private final YakTableSchema schema;

    DebeziumRecordConverter(YakTableSchema schema) {
        this.schema = schema;
    }

    List<YakRow> convert(SourceRecord record) {
        if (!(record.value() instanceof Struct envelope)) {
            return List.of();
        }
        if (envelope.schema().field("op") == null) {
            return List.of();
        }

        String operation = envelope.getString("op");
        return switch (operation) {
            case "r", "c" -> rowIfPresent(RowKind.INSERT, envelope, "after");
            case "u" -> updateRows(envelope);
            case "d" -> rowIfPresent(RowKind.DELETE, envelope, "before");
            default -> List.of();
        };
    }

    private List<YakRow> updateRows(Struct envelope) {
        List<YakRow> rows = new ArrayList<>(2);
        Struct before = struct(envelope, "before");
        Struct after = struct(envelope, "after");
        if (before != null) rows.add(toRow(RowKind.UPDATE_BEFORE, before));
        if (after != null) rows.add(toRow(RowKind.UPDATE_AFTER, after));
        return rows;
    }

    private List<YakRow> rowIfPresent(RowKind rowKind, Struct envelope, String field) {
        Struct row = struct(envelope, field);
        return row == null ? List.of() : List.of(toRow(rowKind, row));
    }

    private Struct struct(Struct envelope, String field) {
        if (envelope.schema().field(field) == null) return null;
        Object value = envelope.get(field);
        return value instanceof Struct struct ? struct : null;
    }

    private YakRow toRow(RowKind rowKind, Struct struct) {
        List<Object> values = new ArrayList<>(schema.columnCount());
        for (YakColumn column : schema.columns()) {
            if (struct.schema().field(column.name()) == null) {
                throw new IllegalArgumentException("CDC event missing column: " + column.name());
            }
            values.add(normalize(column.dataType(), struct.get(column.name())));
        }
        return new YakRow(rowKind, values);
    }

    private Object normalize(YakDataType dataType, Object value) {
        if (value == null) return null;
        if (value instanceof ByteBuffer buffer) {
            ByteBuffer copy = buffer.slice();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            return bytes;
        }
        if (value instanceof Date date) {
            return switch (dataType.kind()) {
                case DATE -> LocalDate.ofInstant(date.toInstant(), ZoneOffset.UTC);
                case TIME -> LocalTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
                case TIMESTAMP -> LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
                case TIMESTAMP_WITH_TIME_ZONE -> OffsetDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
                default -> value;
            };
        }
        return value;
    }
}
