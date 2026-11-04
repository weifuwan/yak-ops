package org.apache.flink.cdc.runtime.serializer;

import org.apache.flink.api.common.typeutils.SimpleTypeSerializerSnapshot;
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;

import java.io.IOException;

/** Type serializer for {@code Double}. */
public final class DoubleSerializer extends TypeSerializerSingleton<Double> {

    private static final long serialVersionUID = 1L;

    /** Sharable instance of the DoubleSerializer. */
    public static final DoubleSerializer INSTANCE = new DoubleSerializer();

    private static final Double ZERO = 0.0;

    @Override
    public boolean isImmutableType() {
        return true;
    }

    @Override
    public Double createInstance() {
        return ZERO;
    }

    @Override
    public Double copy(Double from) {
        return from;
    }

    @Override
    public Double copy(Double from, Double reuse) {
        return from;
    }

    @Override
    public int getLength() {
        return 8;
    }

    @Override
    public void serialize(Double record, DataOutputView target) throws IOException {
        target.writeDouble(record);
    }

    @Override
    public Double deserialize(DataInputView source) throws IOException {
        return source.readDouble();
    }

    @Override
    public Double deserialize(Double reuse, DataInputView source) throws IOException {
        return deserialize(source);
    }

    @Override
    public void copy(DataInputView source, DataOutputView target) throws IOException {
        target.writeDouble(source.readDouble());
    }

    @Override
    public TypeSerializerSnapshot<Double> snapshotConfiguration() {
        return new DoubleSerializerSnapshot();
    }

    // ------------------------------------------------------------------------

    /** Serializer configuration snapshot for compatibility and format evolution. */
    @SuppressWarnings("WeakerAccess")
    public static final class DoubleSerializerSnapshot
            extends SimpleTypeSerializerSnapshot<Double> {

        public DoubleSerializerSnapshot() {
            super(() -> INSTANCE);
        }
    }
}
