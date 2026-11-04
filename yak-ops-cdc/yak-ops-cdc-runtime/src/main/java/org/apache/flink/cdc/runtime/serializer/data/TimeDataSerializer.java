package org.apache.flink.cdc.runtime.serializer.data;

import org.apache.flink.api.common.typeutils.SimpleTypeSerializerSnapshot;
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot;
import org.apache.flink.cdc.common.data.TimeData;
import org.apache.flink.cdc.runtime.serializer.TypeSerializerSingleton;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;

import java.io.IOException;

/** Serializer for {@link TimeData}. */
public final class TimeDataSerializer extends TypeSerializerSingleton<TimeData> {

    private static final long serialVersionUID = 1L;

    public static final TimeDataSerializer INSTANCE = new TimeDataSerializer();

    private TimeDataSerializer() {}

    @Override
    public boolean isImmutableType() {
        return true;
    }

    @Override
    public TimeData createInstance() {
        return TimeData.fromNanoOfDay(0);
    }

    @Override
    public TimeData copy(TimeData from) {
        return TimeData.fromMillisOfDay(from.toMillisOfDay());
    }

    @Override
    public TimeData copy(TimeData from, TimeData reuse) {
        return TimeData.fromMillisOfDay(from.toMillisOfDay());
    }

    @Override
    public int getLength() {
        return 4;
    }

    @Override
    public void serialize(TimeData record, DataOutputView target) throws IOException {
        target.writeInt(record.toMillisOfDay());
    }

    @Override
    public TimeData deserialize(DataInputView source) throws IOException {
        return TimeData.fromMillisOfDay(source.readInt());
    }

    @Override
    public TimeData deserialize(TimeData record, DataInputView source) throws IOException {
        return deserialize(source);
    }

    @Override
    public void copy(DataInputView source, DataOutputView target) throws IOException {
        target.writeInt(source.readInt());
    }

    @Override
    public TypeSerializerSnapshot<TimeData> snapshotConfiguration() {
        return new TimeDataSerializerSnapshot();
    }

    /** Serializer configuration snapshot for compatibility and format evolution. */
    @SuppressWarnings("WeakerAccess")
    public static final class TimeDataSerializerSnapshot
            extends SimpleTypeSerializerSnapshot<TimeData> {

        public TimeDataSerializerSnapshot() {
            super(() -> INSTANCE);
        }
    }
}
