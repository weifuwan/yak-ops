package io.yak.ops.connector.cdc.mysql.source.offset;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Kafka Connect source partition and offset for one delivered MySQL Binlog event.
 *
 * <p>Offsets include finer-grained Debezium event/row positions, not only the Binlog file
 * and byte position. An immutable copy is checkpointed after downstream emission.
 */
public record BinlogOffset(Map<String, Object> partition, Map<String, Object> position)
        implements java.io.Serializable {

    public BinlogOffset {
        partition = immutable(Objects.requireNonNull(partition, "partition"));
        position = immutable(Objects.requireNonNull(position, "position"));
        if (partition.isEmpty() || position.isEmpty()) {
            throw new IllegalArgumentException("MySQL Binlog offset requires a source partition and position");
        }
    }

    private static Map<String, Object> immutable(Map<String, ?> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("MySQL offset keys must be nonblank");
            }
            if (value != null
                    && !(value instanceof String)
                    && !(value instanceof Number)
                    && !(value instanceof Boolean)) {
                throw new IllegalArgumentException("Unsupported MySQL offset field type: " + key);
            }
            result.put(key, value);
        });
        return Collections.unmodifiableMap(result);
    }

    public void writeTo(DataOutputStream out) throws IOException {
        writeMap(out, partition);
        writeMap(out, position);
    }

    public static BinlogOffset readFrom(DataInputStream in) throws IOException {
        return new BinlogOffset(readMap(in), readMap(in));
    }

    private static void writeMap(DataOutputStream out, Map<String, Object> values) throws IOException {
        if (values.size() > 128) {
            throw new IOException("MySQL offset has too many fields");
        }
        out.writeInt(values.size());
        for (var entry : values.entrySet()) {
            out.writeUTF(entry.getKey());
            Object value = entry.getValue();
            if (value == null) {
                out.writeByte(0);
            } else if (value instanceof String text) {
                out.writeByte(1);
                out.writeUTF(text);
            } else if (value instanceof Integer number) {
                out.writeByte(2);
                out.writeInt(number);
            } else if (value instanceof Long number) {
                out.writeByte(3);
                out.writeLong(number);
            } else if (value instanceof Boolean booleanValue) {
                out.writeByte(4);
                out.writeBoolean(booleanValue);
            } else if (value instanceof Short number) {
                out.writeByte(5);
                out.writeShort(number);
            } else if (value instanceof Byte number) {
                out.writeByte(6);
                out.writeByte(number);
            } else if (value instanceof Double number) {
                out.writeByte(7);
                out.writeDouble(number);
            } else if (value instanceof Float number) {
                out.writeByte(8);
                out.writeFloat(number);
            } else {
                throw new IOException("Unsupported Binlog offset field type: " + entry.getKey());
            }
        }
    }

    private static Map<String, Object> readMap(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > 128) {
            throw new IOException("Invalid MySQL Binlog offset field count");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < size; index++) {
            String key = in.readUTF();
            Object value =
                    switch (in.readUnsignedByte()) {
                        case 0 -> null;
                        case 1 -> in.readUTF();
                        case 2 -> in.readInt();
                        case 3 -> in.readLong();
                        case 4 -> in.readBoolean();
                        case 5 -> in.readShort();
                        case 6 -> in.readByte();
                        case 7 -> in.readDouble();
                        case 8 -> in.readFloat();
                        default -> throw new IOException("Unsupported MySQL offset field encoding");
                    };
            if (result.containsKey(key)) {
                throw new IOException("Duplicate MySQL offset field");
            }
            result.put(key, value);
        }
        return result;
    }
}
