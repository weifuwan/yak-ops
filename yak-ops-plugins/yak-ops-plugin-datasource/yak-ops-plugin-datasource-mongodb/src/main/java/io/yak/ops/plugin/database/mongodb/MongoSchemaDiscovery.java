package io.yak.ops.plugin.database.mongodb;

import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.BsonDocument;
import org.bson.BsonType;
import org.bson.BsonValue;

/** Sampled MongoDB schema discovery aligned with the Link-Up bounded connector policy. */
final class MongoSchemaDiscovery {

    static final int SAMPLE_SIZE = 1000;
    static final int MAX_DEPTH = 8;
    private static final int DECIMAL_PRECISION = 34;
    private static final int DECIMAL_SCALE = 18;

    private MongoSchemaDiscovery() {}

    static List<DataSourceColumn> discover(Iterable<BsonDocument> documents) {
        Map<String, FieldState> fields = new LinkedHashMap<>();
        int sampled = 0;
        for (BsonDocument document : documents) {
            if (document == null || sampled >= SAMPLE_SIZE) break;
            sampled++;
            observeDocument(fields, null, document, 0);
        }
        if (fields.isEmpty()) return List.of();

        List<DataSourceColumn> result = new ArrayList<>();
        int ordinal = 1;
        for (Map.Entry<String, FieldState> entry : fields.entrySet()) {
            result.add(entry.getValue().column(entry.getKey(), sampled, ordinal++));
        }
        return List.copyOf(result);
    }

    static Object previewValue(BsonDocument document, String path) {
        BsonValue value = extract(document, path);
        return portableValue(value);
    }

    private static void observeDocument(
            Map<String, FieldState> fields, String parent, BsonDocument document, int depth) {
        for (Map.Entry<String, BsonValue> entry : document.entrySet()) {
            String path = parent == null ? entry.getKey() : parent + "." + entry.getKey();
            FieldState state = fields.computeIfAbsent(path, ignored -> new FieldState());
            BsonValue value = entry.getValue();
            state.observe(value);
            if (value != null && value.getBsonType() == BsonType.DOCUMENT && depth < MAX_DEPTH) {
                observeDocument(fields, path, value.asDocument(), depth + 1);
            }
        }
    }

    private static BsonValue extract(BsonDocument document, String path) {
        if (document == null || path == null || path.isBlank()) return null;
        String[] segments = path.split("\\.");
        BsonDocument current = document;
        for (int i = 0; i < segments.length; i++) {
            BsonValue value = current.get(segments[i]);
            if (value == null || value.getBsonType() == BsonType.NULL) return value;
            if (i == segments.length - 1) return value;
            if (value.getBsonType() != BsonType.DOCUMENT) return null;
            current = value.asDocument();
        }
        return null;
    }

    private static Object portableValue(BsonValue value) {
        if (value == null || value.getBsonType() == BsonType.NULL) return null;
        return switch (value.getBsonType()) {
            case STRING -> value.asString().getValue();
            case OBJECT_ID -> value.asObjectId().getValue().toHexString();
            case BOOLEAN -> value.asBoolean().getValue();
            case INT32 -> value.asInt32().getValue();
            case INT64 -> value.asInt64().getValue();
            case DOUBLE -> value.asDouble().getValue();
            case DECIMAL128 -> value.asDecimal128().getValue().toString();
            case DATE_TIME ->
                Instant.ofEpochMilli(value.asDateTime().getValue()).toString();
            case TIMESTAMP ->
                Instant.ofEpochSecond(value.asTimestamp().getTime()).toString();
            case BINARY -> Base64.getEncoder().encodeToString(value.asBinary().getData());
            case DOCUMENT, ARRAY -> value.toString();
            default -> value.toString();
        };
    }

    private static final class FieldState {

        private final Set<BsonType> physicalTypes = new LinkedHashSet<>();
        private Kind kind = Kind.NULL;
        private int observedDocuments;
        private boolean sawNull;
        private boolean complex;

        void observe(BsonValue value) {
            observedDocuments++;
            BsonType type = value == null ? BsonType.NULL : value.getBsonType();
            physicalTypes.add(type);
            if (type == BsonType.NULL) sawNull = true;
            if (type == BsonType.DOCUMENT || type == BsonType.ARRAY) complex = true;
            kind = Kind.merge(kind, Kind.from(type));
        }

        DataSourceColumn column(String name, int sampledDocuments, int ordinal) {
            boolean nullable = sawNull || observedDocuments < sampledDocuments;
            Integer size = kind == Kind.DECIMAL ? DECIMAL_PRECISION : null;
            Integer scale = kind == Kind.DECIMAL ? DECIMAL_SCALE : null;
            String remarks = complex
                    ? "MongoDB complex value; offline sync uses Extended JSON boundary"
                    : physicalTypes.size() > 1 ? "MongoDB sampled heterogeneous field" : null;
            return new DataSourceColumn(
                    name, typeName(), kind.jdbcType(), size, scale, nullable, ordinal, "_id".equals(name), remarks);
        }

        private String typeName() {
            List<String> names = new ArrayList<>();
            for (BsonType type : physicalTypes) {
                if (type != BsonType.NULL) names.add(display(type));
            }
            if (names.isEmpty()) return "Null";
            return String.join("|", names);
        }

        private static String display(BsonType type) {
            return switch (type) {
                case OBJECT_ID -> "ObjectId";
                case INT32 -> "Int32";
                case INT64 -> "Int64";
                case DECIMAL128 -> "Decimal128";
                case DATE_TIME -> "DateTime";
                case TIMESTAMP -> "Timestamp";
                case BINARY -> "Binary";
                case DOCUMENT -> "Document";
                case ARRAY -> "Array";
                case STRING -> "String";
                case BOOLEAN -> "Boolean";
                case DOUBLE -> "Double";
                default -> type.name();
            };
        }
    }

    private enum Kind {
        NULL,
        BOOLEAN,
        INT,
        LONG,
        DOUBLE,
        DECIMAL,
        TIMESTAMP,
        BYTES,
        JSON,
        STRING;

        static Kind from(BsonType type) {
            return switch (type) {
                case NULL -> NULL;
                case BOOLEAN -> BOOLEAN;
                case INT32 -> INT;
                case INT64 -> LONG;
                case DOUBLE -> DOUBLE;
                case DECIMAL128 -> DECIMAL;
                case DATE_TIME, TIMESTAMP -> TIMESTAMP;
                case BINARY -> BYTES;
                case DOCUMENT, ARRAY -> JSON;
                default -> STRING;
            };
        }

        static Kind merge(Kind left, Kind right) {
            if (left == NULL) return right;
            if (right == NULL) return left;
            if (left == right) return left;
            if (numeric(left) && numeric(right)) {
                if ((left == DECIMAL && right == DOUBLE) || (left == DOUBLE && right == DECIMAL)) {
                    return STRING;
                }
                if (left == DECIMAL || right == DECIMAL) return DECIMAL;
                if (left == DOUBLE || right == DOUBLE) return DOUBLE;
                if (left == LONG || right == LONG) return LONG;
                return INT;
            }
            return STRING;
        }

        private static boolean numeric(Kind value) {
            return value == INT || value == LONG || value == DOUBLE || value == DECIMAL;
        }

        int jdbcType() {
            return switch (this) {
                case BOOLEAN -> Types.BOOLEAN;
                case INT -> Types.INTEGER;
                case LONG -> Types.BIGINT;
                case DOUBLE -> Types.DOUBLE;
                case DECIMAL -> Types.DECIMAL;
                case TIMESTAMP -> Types.TIMESTAMP;
                case BYTES -> Types.VARBINARY;
                case NULL, JSON, STRING -> Types.VARCHAR;
            };
        }
    }
}
