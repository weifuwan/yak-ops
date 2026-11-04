package org.apache.flink.cdc.runtime.typeutils;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.serialization.SerializerConfig;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.cdc.common.event.Event;
import org.apache.flink.cdc.runtime.serializer.event.EventSerializer;

/** A {@link TypeInformation} for {@link Event}. */
public class EventTypeInfo extends TypeInformation<Event> {

    private static final long serialVersionUID = 1L;

    @Override
    public boolean isBasicType() {
        return false;
    }

    @Override
    public boolean isTupleType() {
        return false;
    }

    @Override
    public int getArity() {
        return 0;
    }

    @Override
    public int getTotalFields() {
        return 1;
    }

    @Override
    public Class<Event> getTypeClass() {
        return Event.class;
    }

    @Override
    public boolean isKeyType() {
        return false;
    }

    // Flink 1.x abstract method - not abstract in Flink 2.x so @Override omitted for cross-version
    // compatibility
    public TypeSerializer<Event> createSerializer(ExecutionConfig config) {
        return EventSerializer.INSTANCE;
    }

    /** For Flink 2.2+ (TypeInformation.createSerializer now takes SerializerConfig). */
    @Override
    public TypeSerializer<Event> createSerializer(SerializerConfig config) {
        return EventSerializer.INSTANCE;
    }

    @Override
    public String toString() {
        return "Event";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof EventTypeInfo;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public boolean canEqual(Object obj) {
        return obj instanceof EventTypeInfo;
    }
}
