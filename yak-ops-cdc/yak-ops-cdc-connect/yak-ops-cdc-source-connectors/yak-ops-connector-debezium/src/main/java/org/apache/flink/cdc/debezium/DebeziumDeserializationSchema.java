package org.apache.flink.cdc.debezium;

import org.apache.flink.api.java.typeutils.ResultTypeQueryable;
import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.util.Collector;

import org.apache.kafka.connect.source.SourceRecord;

import java.io.Serializable;

/**
 * The deserialization schema describes how to turn the Debezium SourceRecord into data types
 * (Java/Scala objects) that are processed by Flink.
 *
 * @param <T> The type created by the deserialization schema.
 */
@PublicEvolving
public interface DebeziumDeserializationSchema<T> extends Serializable, ResultTypeQueryable<T> {

    /** Deserialize the Debezium record, it is represented in Kafka {@link SourceRecord}. */
    void deserialize(SourceRecord record, Collector<T> out) throws Exception;
}
