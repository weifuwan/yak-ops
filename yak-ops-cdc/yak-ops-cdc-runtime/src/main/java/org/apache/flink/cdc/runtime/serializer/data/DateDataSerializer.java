package org.apache.flink.cdc.runtime.serializer.data;

import org.apache.flink.api.common.typeutils.SimpleTypeSerializerSnapshot;
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot;
import org.apache.flink.cdc.common.data.DateData;
import org.apache.flink.cdc.runtime.serializer.TypeSerializerSingleton;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;

import java.io.IOException;

/** Serializer for {@link DateData}. */
public final class DateDataSerializer extends TypeSerializerSingleton<DateData> {

    private static final long serialVersionUID = 1L;

    public static final DateDataSerializer INSTANCE = new DateDataSerializer();

    private DateDataSerializer() {}

    @Override
    public boolean isImmutableType() {
        return true;
    }

    @Override
    public DateData createInstance() {
        return DateData.fromEpochDay(0);
    }

    @Override
    public DateData copy(DateData from) {
        return DateData.fromEpochDay(from.toEpochDay());
    }

    @Override
    public DateData copy(DateData from, DateData reuse) {
        return DateData.fromEpochDay(from.toEpochDay());
    }

    @Override
    public int getLength() {
        return 4;
    }

    @Override
    public void serialize(DateData record, DataOutputView target) throws IOException {
        target.writeInt(record.toEpochDay());
    }

    @Override
    public DateData deserialize(DataInputView source) throws IOException {
        return DateData.fromEpochDay(source.readInt());
    }

    @Override
    public DateData deserialize(DateData record, DataInputView source) throws IOException {
        return deserialize(source);
    }

    @Override
    public void copy(DataInputView source, DataOutputView target) throws IOException {
        target.writeInt(source.readInt());
    }

    @Override
    public TypeSerializerSnapshot<DateData> snapshotConfiguration() {
        return new DateDataSerializerSnapshot();
    }

    /** Serializer configuration snapshot for compatibility and format evolution. */
    @SuppressWarnings("WeakerAccess")
    public static final class DateDataSerializerSnapshot
            extends SimpleTypeSerializerSnapshot<DateData> {

        public DateDataSerializerSnapshot() {
            super(() -> INSTANCE);
        }
    }
}
