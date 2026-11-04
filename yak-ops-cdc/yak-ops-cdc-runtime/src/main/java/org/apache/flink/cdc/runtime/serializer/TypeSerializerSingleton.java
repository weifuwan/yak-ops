package org.apache.flink.cdc.runtime.serializer;

import org.apache.flink.api.common.typeutils.TypeSerializer;

/** A {@link TypeSerializer} whose method duplicate() return itself. */
public abstract class TypeSerializerSingleton<T> extends TypeSerializer<T> {

    private static final long serialVersionUID = 8766687317209282373L;

    // --------------------------------------------------------------------------------------------

    @Override
    public TypeSerializerSingleton<T> duplicate() {
        return this;
    }

    @Override
    public int hashCode() {
        return this.getClass().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj.getClass().equals(this.getClass());
    }
}
