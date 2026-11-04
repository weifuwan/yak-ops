package org.apache.flink.cdc.runtime.serializer;

import org.apache.flink.api.common.typeutils.SimpleTypeSerializerSnapshot;
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;

import java.io.IOException;

/** Type serializer for {@code Boolean}. */
public final class BooleanSerializer extends TypeSerializerSingleton<Boolean> {

    private static final long serialVersionUID = 1L;

    /** Sharable instance of the BooleanSerializer. */
    public static final BooleanSerializer INSTANCE = new BooleanSerializer();

    private static final Boolean FALSE = Boolean.FALSE;

    @Override
    public boolean isImmutableType() {
        return true;
    }

    @Override
    public Boolean createInstance() {
        return FALSE;
    }

    @Override
    public Boolean copy(Boolean from) {
        return from;
    }

    @Override
    public Boolean copy(Boolean from, Boolean reuse) {
        return from;
    }

    @Override
    public int getLength() {
        return 1;
    }

    @Override
    public void serialize(Boolean record, DataOutputView target) throws IOException {
        target.writeBoolean(record);
    }

    @Override
    public Boolean deserialize(DataInputView source) throws IOException {
        return source.readBoolean();
    }

    @Override
    public Boolean deserialize(Boolean reuse, DataInputView source) throws IOException {
        return source.readBoolean();
    }

    @Override
    public void copy(DataInputView source, DataOutputView target) throws IOException {
        target.writeBoolean(source.readBoolean());
    }

    @Override
    public TypeSerializerSnapshot<Boolean> snapshotConfiguration() {
        return new BooleanSerializerSnapshot();
    }

    // ------------------------------------------------------------------------

    /** Serializer configuration snapshot for compatibility and format evolution. */
    @SuppressWarnings("WeakerAccess")
    public static final class BooleanSerializerSnapshot
            extends SimpleTypeSerializerSnapshot<Boolean> {

        public BooleanSerializerSnapshot() {
            super(() -> INSTANCE);
        }
    }
}
