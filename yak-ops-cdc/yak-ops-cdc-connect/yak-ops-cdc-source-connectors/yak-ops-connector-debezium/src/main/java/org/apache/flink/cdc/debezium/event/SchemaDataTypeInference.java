package org.apache.flink.cdc.debezium.event;

import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.cdc.common.types.DataType;

import org.apache.kafka.connect.data.Schema;

/** {@link DataType} inference for kafka connect {@link Schema}. */
@Internal
public interface SchemaDataTypeInference {

    /**
     * Infer {@link DataType} from {@link Schema}.
     *
     * @param value the value corresponding value to SCHEMA
     * @param schema the kafka connect schema
     * @return the inferred data type
     */
    DataType infer(Object value, Schema schema);
}
