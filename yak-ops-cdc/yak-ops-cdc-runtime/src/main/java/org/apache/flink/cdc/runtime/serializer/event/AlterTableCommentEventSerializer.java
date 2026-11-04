package org.apache.flink.cdc.runtime.serializer.event;

import org.apache.flink.api.common.typeutils.SimpleTypeSerializerSnapshot;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot;
import org.apache.flink.cdc.common.event.AlterTableCommentEvent;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.runtime.serializer.StringSerializer;
import org.apache.flink.cdc.runtime.serializer.TableIdSerializer;
import org.apache.flink.cdc.runtime.serializer.TypeSerializerSingleton;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;

import java.io.IOException;

/** A {@link TypeSerializer} for {@link AlterTableCommentEvent}. */
public class AlterTableCommentEventSerializer
        extends TypeSerializerSingleton<AlterTableCommentEvent> {

    private static final long serialVersionUID = 1L;

    /** Sharable instance of the TableIdSerializer. */
    public static final AlterTableCommentEventSerializer INSTANCE =
            new AlterTableCommentEventSerializer();

    private final TableIdSerializer tableIdSerializer = TableIdSerializer.INSTANCE;
    private final StringSerializer commentSerializer = StringSerializer.INSTANCE;

    @Override
    public boolean isImmutableType() {
        return false;
    }

    @Override
    public AlterTableCommentEvent createInstance() {
        return new AlterTableCommentEvent(TableId.tableId("unknown"), "");
    }

    @Override
    public AlterTableCommentEvent copy(AlterTableCommentEvent from) {
        return new AlterTableCommentEvent(from.tableId(), from.getComment());
    }

    @Override
    public AlterTableCommentEvent copy(AlterTableCommentEvent from, AlterTableCommentEvent reuse) {
        return copy(from);
    }

    @Override
    public int getLength() {
        return -1;
    }

    @Override
    public void serialize(AlterTableCommentEvent record, DataOutputView target) throws IOException {
        tableIdSerializer.serialize(record.tableId(), target);
        commentSerializer.serialize(record.getComment(), target);
    }

    @Override
    public AlterTableCommentEvent deserialize(DataInputView source) throws IOException {
        return new AlterTableCommentEvent(
                tableIdSerializer.deserialize(source), commentSerializer.deserialize(source));
    }

    @Override
    public AlterTableCommentEvent deserialize(AlterTableCommentEvent reuse, DataInputView source)
            throws IOException {
        return deserialize(source);
    }

    @Override
    public void copy(DataInputView source, DataOutputView target) throws IOException {
        serialize(deserialize(source), target);
    }

    @Override
    public TypeSerializerSnapshot<AlterTableCommentEvent> snapshotConfiguration() {
        return new AlterTableCommentEventSerializerSnapshot();
    }

    /** Serializer configuration snapshot for compatibility and format evolution. */
    @SuppressWarnings("WeakerAccess")
    public static final class AlterTableCommentEventSerializerSnapshot
            extends SimpleTypeSerializerSnapshot<AlterTableCommentEvent> {

        public AlterTableCommentEventSerializerSnapshot() {
            super(() -> INSTANCE);
        }
    }
}
