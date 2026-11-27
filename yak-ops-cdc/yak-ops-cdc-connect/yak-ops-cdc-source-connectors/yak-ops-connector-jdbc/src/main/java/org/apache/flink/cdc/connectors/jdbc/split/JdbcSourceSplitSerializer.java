package org.apache.flink.cdc.connectors.jdbc.split;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.runtime.serializer.TableIdSerializer;
import org.apache.flink.cdc.runtime.serializer.schema.SchemaSerializer;
import org.apache.flink.core.io.SimpleVersionedSerializer;
import org.apache.flink.core.memory.DataInputDeserializer;
import org.apache.flink.core.memory.DataOutputSerializer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.math.BigDecimal;

/** Versioned serializer for JDBC source splits. Version 1 reads the former Java-serialized format. */
public final class JdbcSourceSplitSerializer
        implements SimpleVersionedSerializer<JdbcSourceSplit> {

    private static final int LEGACY_VERSION = 1;
    private static final int CURRENT_VERSION = 2;

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public byte[] serialize(JdbcSourceSplit split) throws IOException {
        DataOutputSerializer out = new DataOutputSerializer(256);
        out.writeUTF(split.splitId());
        TableIdSerializer.INSTANCE.serialize(split.getTableId(), out);
        SchemaSerializer.INSTANCE.serialize(split.getSchema(), out);
        writeNullableString(split.getSplitColumn(), out);
        writeNullableDecimal(split.getLowerBound(), out);
        writeNullableDecimal(split.getUpperBound(), out);
        out.writeBoolean(split.isUpperInclusive());
        writeNullableDecimal(split.getLastReadValue(), out);
        return out.getCopyOfBuffer();
    }

    @Override
    public JdbcSourceSplit deserialize(int version, byte[] serialized) throws IOException {
        if (version == LEGACY_VERSION) {
            return deserializeLegacy(serialized);
        }
        if (version != CURRENT_VERSION) {
            throw new IOException("Unsupported JDBC source split version: " + version);
        }

        DataInputDeserializer in = new DataInputDeserializer(serialized);
        String splitId = in.readUTF();
        TableId tableId = TableIdSerializer.INSTANCE.deserialize(in);
        Schema schema = SchemaSerializer.INSTANCE.deserialize(in);
        String splitColumn = readNullableString(in);
        BigDecimal lowerBound = readNullableDecimal(in);
        BigDecimal upperBound = readNullableDecimal(in);
        boolean upperInclusive = in.readBoolean();
        BigDecimal lastReadValue = readNullableDecimal(in);
        return new JdbcSourceSplit(
                splitId,
                tableId,
                schema,
                splitColumn,
                lowerBound,
                upperBound,
                upperInclusive,
                lastReadValue);
    }

    public static JdbcSourceSplit fromLegacy(
            org.apache.flink.cdc.connectors.jdbc.source.JdbcSourceSplit split) {
        return new JdbcSourceSplit(
                split.splitId(),
                split.getTableId(),
                split.getSchema(),
                split.getSplitColumn(),
                split.getLowerBound(),
                split.getUpperBound(),
                split.isUpperInclusive(),
                split.getLastReadValue());
    }

    private JdbcSourceSplit deserializeLegacy(byte[] serialized) throws IOException {
        try (ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object value = input.readObject();
            if (!(value instanceof org.apache.flink.cdc.connectors.jdbc.source.JdbcSourceSplit)) {
                throw new IOException(
                        "Unexpected legacy JDBC split type: " + value.getClass().getName());
            }
            return fromLegacy(
                    (org.apache.flink.cdc.connectors.jdbc.source.JdbcSourceSplit) value);
        } catch (ClassNotFoundException e) {
            throw new IOException("Could not deserialize legacy JDBC source split", e);
        }
    }

    private static void writeNullableString(String value, DataOutputSerializer out)
            throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeUTF(value);
        }
    }

    private static String readNullableString(DataInputDeserializer in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }

    private static void writeNullableDecimal(BigDecimal value, DataOutputSerializer out)
            throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeUTF(value.toString());
        }
    }

    private static BigDecimal readNullableDecimal(DataInputDeserializer in) throws IOException {
        return in.readBoolean() ? new BigDecimal(in.readUTF()) : null;
    }
}
