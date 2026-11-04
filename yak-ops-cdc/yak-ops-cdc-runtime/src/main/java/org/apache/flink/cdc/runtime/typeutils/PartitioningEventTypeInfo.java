package org.apache.flink.cdc.runtime.typeutils;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.serialization.SerializerConfig;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.cdc.runtime.partitioning.PartitioningEvent;
import org.apache.flink.cdc.runtime.serializer.event.PartitioningEventSerializer;

/** Type information for {@link PartitioningEvent}. */
@Internal
public class PartitioningEventTypeInfo extends TypeInformation<PartitioningEvent> {

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
        return 2;
    }

    @Override
    public int getTotalFields() {
        return 2;
    }

    @Override
    public Class<PartitioningEvent> getTypeClass() {
        return PartitioningEvent.class;
    }

    @Override
    public boolean isKeyType() {
        return false;
    }

    // Flink 1.x abstract method - not abstract in Flink 2.x so @Override omitted for cross-version
    // compatibility
    public TypeSerializer<PartitioningEvent> createSerializer(ExecutionConfig config) {
        return PartitioningEventSerializer.INSTANCE;
    }

    /** For Flink 2.2+ (TypeInformation.createSerializer now takes SerializerConfig). */
    @Override
    public TypeSerializer<PartitioningEvent> createSerializer(SerializerConfig config) {
        return PartitioningEventSerializer.INSTANCE;
    }

    @Override
    public String toString() {
        return "PartitioningEvent";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PartitioningEvent;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public boolean canEqual(Object obj) {
        return obj instanceof PartitioningEvent;
    }
}
