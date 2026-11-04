package org.apache.flink.cdc.common.function;

import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.cdc.common.event.DataChangeEvent;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.sink.DataSink;

import javax.annotation.Nullable;

import java.io.Serializable;

/**
 * Provider that provides {@link HashFunction} to help {@code PrePartitionOperator} to shuffle event
 * to designated subtask. This is usually beneficial for load balancing, when writing to different
 * partitions/buckets in {@link DataSink}, add custom implementation to further improve efficiency.
 *
 * @param <T> the type of given element
 */
@Internal
public interface HashFunctionProvider<T> extends Serializable {

    /**
     * Gets a hash function based on the given table ID and schema, to help {@code
     * PrePartitionOperator} to shuffle {@link DataChangeEvent} to designated subtask.
     *
     * @param tableId table ID
     * @param schema flink table schema
     * @return hash function based on the given table ID and schema
     */
    HashFunction<T> getHashFunction(@Nullable TableId tableId, Schema schema);
}
