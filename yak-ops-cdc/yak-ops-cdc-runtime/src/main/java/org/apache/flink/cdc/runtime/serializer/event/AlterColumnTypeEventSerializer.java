package org.apache.flink.cdc.runtime.serializer.event;

import org.apache.flink.api.common.typeutils.SimpleTypeSerializerSnapshot;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot;
import org.apache.flink.cdc.common.event.AlterColumnTypeEvent;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.runtime.serializer.MapSerializer;
import org.apache.flink.cdc.runtime.serializer.StringSerializer;
import org.apache.flink.cdc.runtime.serializer.TableIdSerializer;
import org.apache.flink.cdc.runtime.serializer.TypeSerializerSingleton;
import org.apache.flink.cdc.runtime.serializer.schema.DataTypeSerializer;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;

import java.io.IOException;
import java.util.Collections;

/** A {@link TypeSerializer} for {@link AlterColumnTypeEvent}. */
public class AlterColumnTypeEventSerializer extends TypeSerializerSingleton<AlterColumnTypeEvent> {

    private static final long serialVersionUID = 1L;

    /** Sharable instance of the TableIdSerializer. */
    public static final AlterColumnTypeEventSerializer INSTANCE =
            new AlterColumnTypeEventSerializer();

    private final TableIdSerializer tableIdSerializer = TableIdSerializer.INSTANCE;
    private final MapSerializer<String, DataType> typeMapSerializer =
            new MapSerializer<>(StringSerializer.INSTANCE, new DataTypeSerializer());
    private final MapSerializer<String, String> commentsSerializer =
            new MapSerializer<>(StringSerializer.INSTANCE, StringSerializer.INSTANCE);

    @Override
    public boolean isImmutableType() {
        return false;
    }

    @Override
    public AlterColumnTypeEvent createInstance() {
        return new AlterColumnTypeEvent(TableId.tableId("unknown"), Collections.emptyMap());
    }

    @Override
    public AlterColumnTypeEvent copy(AlterColumnTypeEvent from) {
        return new AlterColumnTypeEvent(
                from.tableId(),
                typeMapSerializer.copy(from.getTypeMapping()),
                typeMapSerializer.copy(from.getOldTypeMapping()),
                commentsSerializer.copy(from.getComments()));
    }

    @Override
    public AlterColumnTypeEvent copy(AlterColumnTypeEvent from, AlterColumnTypeEvent reuse) {
        return copy(from);
    }

    @Override
    public int getLength() {
        return -1;
    }

    @Override
    public void serialize(AlterColumnTypeEvent record, DataOutputView target) throws IOException {
        tableIdSerializer.serialize(record.tableId(), target);
        typeMapSerializer.serialize(record.getTypeMapping(), target);
        typeMapSerializer.serialize(record.getOldTypeMapping(), target);
        commentsSerializer.serialize(record.getComments(), target);
    }

    @Override
    public AlterColumnTypeEvent deserialize(DataInputView source) throws IOException {
        return new AlterColumnTypeEvent(
                tableIdSerializer.deserialize(source),
                typeMapSerializer.deserialize(source),
                typeMapSerializer.deserialize(source),
                commentsSerializer.deserialize(source));
    }

    @Override
    public AlterColumnTypeEvent deserialize(AlterColumnTypeEvent reuse, DataInputView source)
            throws IOException {
        return deserialize(source);
    }

    @Override
    public void copy(DataInputView source, DataOutputView target) throws IOException {
        serialize(deserialize(source), target);
    }

    @Override
    public TypeSerializerSnapshot<AlterColumnTypeEvent> snapshotConfiguration() {
        return new AlterColumnTypeEventSerializerSnapshot();
    }

    /** Serializer configuration snapshot for compatibility and format evolution. */
    @SuppressWarnings("WeakerAccess")
    public static final class AlterColumnTypeEventSerializerSnapshot
            extends SimpleTypeSerializerSnapshot<AlterColumnTypeEvent> {

        public AlterColumnTypeEventSerializerSnapshot() {
            super(() -> INSTANCE);
        }
    }
}
