package org.apache.flink.cdc.connectors.jdbc.enumerator;

import org.apache.flink.cdc.connectors.jdbc.split.JdbcSourceSplit;
import org.apache.flink.cdc.connectors.jdbc.split.JdbcSourceSplitSerializer;
import org.apache.flink.core.io.SimpleVersionedSerializer;
import org.apache.flink.core.memory.DataInputDeserializer;
import org.apache.flink.core.memory.DataOutputSerializer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Versioned serializer for pending JDBC enumerator state. */
public final class JdbcEnumeratorStateSerializer
        implements SimpleVersionedSerializer<JdbcEnumeratorState> {

    private static final int LEGACY_VERSION = 1;
    private static final int CURRENT_VERSION = 2;

    private final JdbcSourceSplitSerializer splitSerializer = new JdbcSourceSplitSerializer();

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public byte[] serialize(JdbcEnumeratorState state) throws IOException {
        DataOutputSerializer out = new DataOutputSerializer(512);
        out.writeBoolean(state.isEnumerationCompleted());
        writeSplits(state.getPendingSplits(), out);

        Map<Integer, List<JdbcSourceSplit>> pendingByReader =
                new TreeMap<>(state.getPendingSplitsByReader());
        out.writeInt(pendingByReader.size());
        for (Map.Entry<Integer, List<JdbcSourceSplit>> entry : pendingByReader.entrySet()) {
            out.writeInt(entry.getKey());
            writeSplits(entry.getValue(), out);
        }
        return out.getCopyOfBuffer();
    }

    @Override
    public JdbcEnumeratorState deserialize(int version, byte[] serialized) throws IOException {
        if (version == LEGACY_VERSION) {
            return deserializeLegacy(serialized);
        }
        if (version != CURRENT_VERSION) {
            throw new IOException("Unsupported JDBC enumerator state version: " + version);
        }

        DataInputDeserializer in = new DataInputDeserializer(serialized);
        boolean enumerationCompleted = in.readBoolean();
        List<JdbcSourceSplit> pendingSplits = readSplits(in);
        int readerCount = readCount(in, "reader");
        Map<Integer, List<JdbcSourceSplit>> pendingByReader = new TreeMap<>();
        for (int i = 0; i < readerCount; i++) {
            int readerId = in.readInt();
            pendingByReader.put(readerId, readSplits(in));
        }
        return new JdbcEnumeratorState(pendingSplits, pendingByReader, enumerationCompleted);
    }

    private JdbcEnumeratorState deserializeLegacy(byte[] serialized) throws IOException {
        try (ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object value = input.readObject();
            if (!(value instanceof org.apache.flink.cdc.connectors.jdbc.source.JdbcEnumeratorState)) {
                throw new IOException(
                        "Unexpected legacy JDBC enumerator state type: "
                                + value.getClass().getName());
            }
            org.apache.flink.cdc.connectors.jdbc.source.JdbcEnumeratorState legacy =
                    (org.apache.flink.cdc.connectors.jdbc.source.JdbcEnumeratorState) value;
            List<JdbcSourceSplit> pendingSplits = convertSplits(legacy.getPendingSplits());
            Map<Integer, List<JdbcSourceSplit>> pendingByReader = new TreeMap<>();
            legacy.getPendingSplitsByReader().forEach(
                    (reader, splits) -> pendingByReader.put(reader, convertSplits(splits)));
            return new JdbcEnumeratorState(
                    pendingSplits, pendingByReader, legacy.isEnumerationCompleted());
        } catch (ClassNotFoundException e) {
            throw new IOException("Could not deserialize legacy JDBC enumerator state", e);
        }
    }

    private List<JdbcSourceSplit> convertSplits(
            List<org.apache.flink.cdc.connectors.jdbc.source.JdbcSourceSplit> legacySplits) {
        List<JdbcSourceSplit> splits = new ArrayList<>(legacySplits.size());
        for (org.apache.flink.cdc.connectors.jdbc.source.JdbcSourceSplit split : legacySplits) {
            splits.add(JdbcSourceSplitSerializer.fromLegacy(split));
        }
        return splits;
    }

    private void writeSplits(List<JdbcSourceSplit> splits, DataOutputSerializer out)
            throws IOException {
        out.writeInt(splits.size());
        for (JdbcSourceSplit split : splits) {
            byte[] bytes = splitSerializer.serialize(split);
            out.writeInt(bytes.length);
            out.write(bytes);
        }
    }

    private List<JdbcSourceSplit> readSplits(DataInputDeserializer in) throws IOException {
        int splitCount = readCount(in, "split");
        List<JdbcSourceSplit> splits = new ArrayList<>(splitCount);
        for (int i = 0; i < splitCount; i++) {
            int length = in.readInt();
            if (length < 0 || length > in.available()) {
                throw new IOException("Invalid JDBC split payload length: " + length);
            }
            byte[] bytes = new byte[length];
            in.readFully(bytes);
            splits.add(splitSerializer.deserialize(splitSerializer.getVersion(), bytes));
        }
        return splits;
    }

    private int readCount(DataInputDeserializer in, String description) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > 10000000) {
            throw new IOException("Invalid JDBC " + description + " count: " + count);
        }
        return count;
    }
}
