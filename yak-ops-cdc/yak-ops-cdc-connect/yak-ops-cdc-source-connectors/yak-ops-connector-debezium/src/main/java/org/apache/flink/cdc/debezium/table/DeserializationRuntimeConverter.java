package org.apache.flink.cdc.debezium.table;

import org.apache.kafka.connect.data.Schema;

import java.io.Serializable;

/**
 * Runtime converter that converts objects of Debezium into objects of Flink Table & SQL internal
 * data structures.
 */
@FunctionalInterface
public interface DeserializationRuntimeConverter extends Serializable {
    Object convert(Object dbzObj, Schema schema) throws Exception;
}
