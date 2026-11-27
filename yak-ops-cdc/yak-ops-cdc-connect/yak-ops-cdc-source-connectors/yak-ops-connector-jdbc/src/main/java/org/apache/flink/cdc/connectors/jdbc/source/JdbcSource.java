package org.apache.flink.cdc.connectors.jdbc.source;

import org.apache.flink.api.connector.source.Boundedness;
import org.apache.flink.api.connector.source.Source;
import org.apache.flink.api.connector.source.SourceReader;
import org.apache.flink.api.connector.source.SourceReaderContext;
import org.apache.flink.api.connector.source.SplitEnumerator;
import org.apache.flink.api.connector.source.SplitEnumeratorContext;
import org.apache.flink.cdc.common.event.Event;
import org.apache.flink.core.io.SimpleVersionedSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/** Bounded Flink Source for reading database tables through JDBC. */
public final class JdbcSource implements Source<Event, JdbcSourceSplit, JdbcEnumeratorState> {

    private static final long serialVersionUID = 1L;
    private final JdbcSourceConfig config;

    public JdbcSource(JdbcSourceConfig config) {
        this.config = config;
    }

    @Override
    public Boundedness getBoundedness() {
        return Boundedness.BOUNDED;
    }

    @Override
    public SourceReader<Event, JdbcSourceSplit> createReader(SourceReaderContext readerContext) {
        return new JdbcSourceReader(config);
    }

    @Override
    public SplitEnumerator<JdbcSourceSplit, JdbcEnumeratorState> createEnumerator(
            SplitEnumeratorContext<JdbcSourceSplit> enumContext) {
        return new JdbcSplitEnumerator(enumContext, config, null);
    }

    @Override
    public SplitEnumerator<JdbcSourceSplit, JdbcEnumeratorState> restoreEnumerator(
            SplitEnumeratorContext<JdbcSourceSplit> enumContext, JdbcEnumeratorState checkpoint) {
        return new JdbcSplitEnumerator(enumContext, config, checkpoint);
    }

    @Override
    public SimpleVersionedSerializer<JdbcSourceSplit> getSplitSerializer() {
        return new JavaSerializer<>();
    }

    @Override
    public SimpleVersionedSerializer<JdbcEnumeratorState> getEnumeratorCheckpointSerializer() {
        return new JavaSerializer<>();
    }

    private static final class JavaSerializer<T> implements SimpleVersionedSerializer<T> {

        @Override
        public int getVersion() { return 1; }

        @Override
        public byte[] serialize(T value) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
                output.writeObject(value);
            }
            return bytes.toByteArray();
        }

        @Override
        @SuppressWarnings("unchecked")
        public T deserialize(int version, byte[] serialized) throws IOException {
            if (version != 1) throw new IOException("Unsupported JDBC source state version: " + version);
            try (ObjectInputStream input =
                    new ObjectInputStream(new ByteArrayInputStream(serialized))) {
                return (T) input.readObject();
            } catch (ClassNotFoundException e) {
                throw new IOException("Could not deserialize JDBC source state", e);
            }
        }
    }
}
