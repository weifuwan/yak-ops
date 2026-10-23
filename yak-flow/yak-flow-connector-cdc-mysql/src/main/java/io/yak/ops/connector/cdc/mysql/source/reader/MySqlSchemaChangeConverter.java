package io.yak.ops.connector.cdc.mysql.source.reader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.SourceSchemaChange;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.BasicType;
import io.yak.ops.core.types.DecimalType;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.TimeType;
import io.yak.ops.core.types.TimestampType;
import io.yak.ops.core.types.VarBinaryType;
import io.yak.ops.core.types.VarCharType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.kafka.connect.data.Struct;

/**
 * Reads Debezium's structured tableChanges metadata, never parses or forwards raw DDL SQL.
 *
 * <p>Only existing-table ADD nullable ordinary columns and same-family safe widening may reach
 * the Sink's JdbcSchemaDerivator. Any unsupported metadata fails closed before an offset advances.
 */
public final class MySqlSchemaChangeConverter {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final Set<TableId> selected;

    public MySqlSchemaChangeConverter(Set<TableId> selected) {
        this.selected = Set.copyOf(Objects.requireNonNull(selected, "selected"));
    }

    public SourceSchemaChange convert(Struct envelope, BinlogOffset offset, Map<TableId, Schema> current) {
        Objects.requireNonNull(envelope, "envelope");
        if (envelope.schema().field("tableChanges") == null) {
            throw new UnsupportedOperationException("Debezium DDL has no structured tableChanges metadata");
        }
        Object raw = envelope.get("tableChanges");
        if (!(raw instanceof List<?> changes)) {
            throw new UnsupportedOperationException("Debezium DDL has no table change descriptions");
        }
        // Debezium's structured list can legitimately be empty for database-level DDL
        // that changes no captured table. Only a nonempty table change is actionable;
        // missing or malformed metadata still fails closed without parsing raw SQL.
        if (changes.isEmpty()) {
            return null;
        }
        SourceSchemaChange found = null;
        for (Object item : changes) {
            if (!(item instanceof Struct change)) {
                throw new UnsupportedOperationException("Unsupported Debezium tableChanges encoding");
            }
            String id = text(change, "id");
            TableId table = resolveTable(id);
            if (!selected.contains(table)) {
                continue;
            }
            if (found != null) {
                throw new UnsupportedOperationException("Multi-table MySQL DDL requires explicit per-table ordering");
            }
            String operation = text(change, "type");
            if (!"ALTER".equalsIgnoreCase(operation)) {
                throw new UnsupportedOperationException("MySQL CDC does not support CREATE, DROP or RENAME at runtime");
            }
            Schema previous = Objects.requireNonNull(current.get(table), "Configured CDC table schema is missing");
            Schema evolved = tableSchema(change, previous);
            if (previous.equals(evolved)) {
                throw new UnsupportedOperationException("MySQL DDL changed unsupported physical schema metadata");
            }
            found = new SourceSchemaChange(eventId(offset, table), table, previous, evolved);
        }
        return found;
    }

    private static TableId resolveTable(String id) {
        int delimiter = id.indexOf('.');
        if (delimiter < 1 || delimiter == id.length() - 1 || id.indexOf('.', delimiter + 1) >= 0) {
            throw new UnsupportedOperationException("Unsupported Debezium table identity");
        }
        return new TableId(id.substring(0, delimiter), null, id.substring(delimiter + 1));
    }

    private static Schema tableSchema(Struct change, Schema previous) {
        if (change.schema().field("table") == null) {
            throw new UnsupportedOperationException("Debezium DDL has no table metadata");
        }
        Object document = change.get("table");
        JsonNode table;
        try {
            if (document instanceof String json) {
                table = JSON.readTree(json);
            } else if (document instanceof Map<?, ?> map) {
                table = JSON.valueToTree(map);
            } else {
                throw new UnsupportedOperationException("Unsupported Debezium schema table metadata representation");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalArgumentException("Invalid Debezium table schema metadata", failure);
        }
        JsonNode columnsNode = table.path("columns");
        JsonNode keysNode = table.path("primaryKeyColumnNames");
        if (!columnsNode.isArray() || !keysNode.isArray() || columnsNode.isEmpty()) {
            throw new UnsupportedOperationException("Debezium table metadata lacks columns or primary keys");
        }
        List<String> keys = new ArrayList<>();
        keysNode.forEach(node -> keys.add(node.asText()));
        if (!previous.primaryKeys().equals(keys)) {
            throw new UnsupportedOperationException("Runtime MySQL CDC cannot change table primary keys");
        }

        List<PhysicalColumn> columns = new ArrayList<>();
        for (JsonNode metadata : columnsNode) {
            String name = requiredText(metadata, "name");
            String nativeType = requiredText(metadata, "typeName").toLowerCase(java.util.Locale.ROOT);
            if (nativeType.contains("unsigned") || nativeType.startsWith("enum") || nativeType.startsWith("set")) {
                throw new UnsupportedOperationException("Unsupported MySQL native type in CDC DDL");
            }
            if (metadata.path("autoIncremented").asBoolean(false)
                    || metadata.path("generated").asBoolean(false)
                    || !metadata.path("defaultValueExpression").isNull()
                            && metadata.hasNonNull("defaultValueExpression")) {
                throw new UnsupportedOperationException("CDC schema evolution cannot change generated/default columns");
            }
            boolean nullable = metadata.path("optional").asBoolean(true);
            int jdbcType = metadata.path("jdbcType").asInt(Integer.MIN_VALUE);
            LogicalType type = logicalType(jdbcType, metadata, nullable);
            PhysicalColumn existing = previous.column(name).orElse(null);
            columns.add(new PhysicalColumn(
                    name,
                    type,
                    existing == null ? null : existing.comment(),
                    existing == null ? null : existing.properties()));
        }
        return new Schema(columns, keys, previous.comment());
    }

    private static LogicalType logicalType(int jdbc, JsonNode column, boolean nullable) {
        int length = column.path("length").asInt(0);
        int scale = column.path("scale").asInt(0);
        return switch (jdbc) {
            case Types.BOOLEAN, Types.BIT -> new BasicType(nullable, LogicalTypeRoot.BOOLEAN);
            case Types.TINYINT -> new BasicType(nullable, LogicalTypeRoot.TINYINT);
            case Types.SMALLINT -> new BasicType(nullable, LogicalTypeRoot.SMALLINT);
            case Types.INTEGER -> new BasicType(nullable, LogicalTypeRoot.INTEGER);
            case Types.BIGINT -> new BasicType(nullable, LogicalTypeRoot.BIGINT);
            case Types.FLOAT, Types.REAL -> new BasicType(nullable, LogicalTypeRoot.FLOAT);
            case Types.DOUBLE -> new BasicType(nullable, LogicalTypeRoot.DOUBLE);
            case Types.DECIMAL, Types.NUMERIC -> new DecimalType(nullable, length, scale);
            case Types.CHAR, Types.NCHAR -> new io.yak.ops.core.types.CharType(nullable, length);
            case Types.VARCHAR, Types.NVARCHAR, Types.LONGVARCHAR, Types.LONGNVARCHAR ->
                new VarCharType(nullable, length > 0 ? length : VarCharType.MAX_LENGTH);
            case Types.BINARY -> new io.yak.ops.core.types.BinaryType(nullable, length);
            case Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB ->
                new VarBinaryType(nullable, length > 0 ? length : VarBinaryType.MAX_LENGTH);
            case Types.DATE -> new BasicType(nullable, LogicalTypeRoot.DATE);
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> new TimeType(nullable, Math.min(6, Math.max(0, scale)));
            case Types.TIMESTAMP -> new TimestampType(nullable, Math.min(6, Math.max(0, scale)));
            default -> throw new UnsupportedOperationException("Unsupported MySQL CDC JDBC logical type: " + jdbc);
        };
    }

    private static String text(Struct struct, String field) {
        if (struct.schema().field(field) == null) {
            throw new UnsupportedOperationException("Debezium schema change is missing " + field);
        }
        Object value = struct.get(field);
        if (!(value instanceof String result) || result.isBlank()) {
            throw new UnsupportedOperationException("Invalid Debezium schema change " + field);
        }
        return result;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new UnsupportedOperationException("Debezium column metadata is missing " + field);
        }
        return value.textValue();
    }

    /** Hashes the complete Connect offset, including intra-binlog event counters. */
    static String eventId(BinlogOffset offset, TableId table) {
        StringBuilder data = new StringBuilder();
        for (Map<String, Object> part : List.of(offset.partition(), offset.position())) {
            part.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                data.append(entry.getKey().length()).append(':').append(entry.getKey());
                String value = Objects.toString(entry.getValue(), "null");
                data.append(value.length()).append(':').append(value);
            });
        }
        data.append(table.catalog()).append('.').append(table.table());
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256").digest(data.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
