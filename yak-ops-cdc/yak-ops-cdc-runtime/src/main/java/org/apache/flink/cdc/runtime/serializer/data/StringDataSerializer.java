package org.apache.flink.cdc.runtime.serializer.data;

import org.apache.flink.api.common.typeutils.SimpleTypeSerializerSnapshot;
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot;
import org.apache.flink.cdc.common.data.StringData;
import org.apache.flink.cdc.common.data.binary.BinarySegmentUtils;
import org.apache.flink.cdc.common.data.binary.BinaryStringData;
import org.apache.flink.cdc.runtime.serializer.TypeSerializerSingleton;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;

import java.io.IOException;

/** Serializer for {@link StringData}. */
public final class StringDataSerializer extends TypeSerializerSingleton<StringData> {

    private static final long serialVersionUID = 1L;

    public static final StringDataSerializer INSTANCE = new StringDataSerializer();

    private StringDataSerializer() {}

    @Override
    public boolean isImmutableType() {
        return true;
    }

    @Override
    public StringData createInstance() {
        return BinaryStringData.fromString("");
    }

    @Override
    public StringData copy(StringData from) {
        // BinaryStringData is the only implementation of StringData
        return ((BinaryStringData) from).copy();
    }

    @Override
    public StringData copy(StringData from, StringData reuse) {
        // BinaryStringData is the only implementation of StringData
        return ((BinaryStringData) from).copy();
    }

    @Override
    public int getLength() {
        return -1;
    }

    @Override
    public void serialize(StringData record, DataOutputView target) throws IOException {
        // BinaryStringData is the only implementation of StringData
        BinaryStringData string = (BinaryStringData) record;
        string.ensureMaterialized();
        target.writeInt(string.getSizeInBytes());
        BinarySegmentUtils.copyToView(
                string.getSegments(), string.getOffset(), string.getSizeInBytes(), target);
    }

    @Override
    public StringData deserialize(DataInputView source) throws IOException {
        return deserializeInternal(source);
    }

    public static StringData deserializeInternal(DataInputView source) throws IOException {
        int length = source.readInt();
        byte[] bytes = new byte[length];
        source.readFully(bytes);
        // BinaryStringData is the only implementation of StringData
        return BinaryStringData.fromBytes(bytes);
    }

    @Override
    public StringData deserialize(StringData record, DataInputView source) throws IOException {
        return deserialize(source);
    }

    @Override
    public void copy(DataInputView source, DataOutputView target) throws IOException {
        int length = source.readInt();
        target.writeInt(length);
        target.write(source, length);
    }

    @Override
    public TypeSerializerSnapshot<StringData> snapshotConfiguration() {
        return new StringDataSerializerSnapshot();
    }

    /** Serializer configuration snapshot for compatibility and format evolution. */
    @SuppressWarnings("WeakerAccess")
    public static final class StringDataSerializerSnapshot
            extends SimpleTypeSerializerSnapshot<StringData> {

        public StringDataSerializerSnapshot() {
            super(() -> INSTANCE);
        }
    }
}
