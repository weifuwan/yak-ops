package org.apache.flink.cdc.common.event;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

import java.io.Serializable;
import java.util.List;

/** Deserializer to deserialize given record to {@link Event}. */
@PublicEvolving
public interface EventDeserializer<T> extends Serializable {

    /** Deserialize given record to {@link Event}s. */
    List<? extends Event> deserialize(T record) throws Exception;
}
