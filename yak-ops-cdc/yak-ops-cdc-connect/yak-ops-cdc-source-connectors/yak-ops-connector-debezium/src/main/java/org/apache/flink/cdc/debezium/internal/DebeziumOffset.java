package org.apache.flink.cdc.debezium.internal;

import org.apache.flink.cdc.common.annotation.Internal;

import java.io.Serializable;
import java.util.Map;

/**
 * The state that the Flink Debezium Consumer holds for each instance.
 *
 * <p>This class describes the most basic state that Debezium used for recovering based on Kafka
 * Connect mechanism. It includes a sourcePartition and sourceOffset.
 *
 * <p>The sourcePartition represents a single input sourcePartition that the record came from (e.g.
 * a filename, table name, or topic-partition). The sourceOffset represents a position in that
 * sourcePartition which can be used to resume consumption of data.
 *
 * <p>These values can have arbitrary structure and should be represented using
 * org.apache.kafka.connect.data objects (or primitive values). For example, a database connector
 * might specify the sourcePartition as a record containing { "db": "database_name", "table":
 * "table_name"} and the sourceOffset as a Long containing the timestamp of the row.
 */
@Internal
public class DebeziumOffset implements Serializable {
    private static final long serialVersionUID = 1L;

    public Map<String, ?> sourcePartition;
    public Map<String, ?> sourceOffset;

    public void setSourcePartition(Map<String, ?> sourcePartition) {
        this.sourcePartition = sourcePartition;
    }

    public void setSourceOffset(Map<String, ?> sourceOffset) {
        this.sourceOffset = sourceOffset;
    }

    @Override
    public String toString() {
        return "DebeziumOffset{"
                + "sourcePartition="
                + sourcePartition
                + ", sourceOffset="
                + sourceOffset
                + '}';
    }
}
