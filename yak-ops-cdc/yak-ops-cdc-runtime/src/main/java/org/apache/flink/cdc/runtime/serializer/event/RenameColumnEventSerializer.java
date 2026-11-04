package org.apache.flink.cdc.runtime.serializer.event;

import org.apache.flink.api.common.typeutils.SimpleTypeSerializerSnapshot;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot;
import org.apache.flink.cdc.common.event.RenameColumnEvent;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.runtime.serializer.MapSerializer;
import org.apache.flink.cdc.runtime.serializer.StringSerializer;
import org.apache.flink.cdc.runtime.serializer.TableIdSerializer;
import org.apache.flink.cdc.runtime.serializer.TypeSerializerSingleton;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;

import java.io.IOException;
import java.util.Collections;

/** A {@link TypeSerializer} for {@link RenameColumnEvent}. */
public class RenameColumnEventSerializer extends TypeSerializerSingleton<RenameColumnEvent> {

    private static final long serialVersionUID = 1L;

    /** Sharable instance of the TableIdSerializer. */
    public static final RenameColumnEventSerializer INSTANCE = new RenameColumnEventSerializer();

    private final TableIdSerializer tableIdSerializer = TableIdSerializer.INSTANCE;
    private final MapSerializer<String, String> nameMapSerializer =
            new MapSerializer<>(StringSerializer.INSTANCE, StringSerializer.INSTANCE);

    @Override
    public boolean isImmutableType() {
        return false;
    }

    @Override
    public RenameColumnEvent createInstance() {
        return new RenameColumnEvent(TableId.tableId("unknown"), Collections.emptyMap());
    }

    @Override
    public RenameColumnEvent copy(RenameColumnEvent from) {
        return new RenameColumnEvent(from.tableId(), nameMapSerializer.copy(from.getNameMapping()));
    }

    @Override
    public RenameColumnEvent copy(RenameColumnEvent from, RenameColumnEvent reuse) {
        return copy(from);
    }

    @Override
    public int getLength() {
        return -1;
    }

    @Override
    public void serialize(RenameColumnEvent record, DataOutputView target) throws IOException {
        tableIdSerializer.serialize(record.tableId(), target);
        nameMapSerializer.serialize(record.getNameMapping(), target);
    }

    @Override
    public RenameColumnEvent deserialize(DataInputView source) throws IOException {
        return new RenameColumnEvent(
                tableIdSerializer.deserialize(source), nameMapSerializer.deserialize(source));
    }

    @Override
    public RenameColumnEvent deserialize(RenameColumnEvent reuse, DataInputView source)
            throws IOException {
        return deserialize(source);
    }

    @Override
    public void copy(DataInputView source, DataOutputView target) throws IOException {
        serialize(deserialize(source), target);
    }

    @Override
    public TypeSerializerSnapshot<RenameColumnEvent> snapshotConfiguration() {
        return new RenameColumnEventSerializerSnapshot();
    }

    /** Serializer configuration snapshot for compatibility and format evolution. */
    @SuppressWarnings("WeakerAccess")
    public static final class RenameColumnEventSerializerSnapshot
            extends SimpleTypeSerializerSnapshot<RenameColumnEvent> {

        public RenameColumnEventSerializerSnapshot() {
            super(() -> INSTANCE);
        }
    }
}
