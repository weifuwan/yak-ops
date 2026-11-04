package org.apache.flink.cdc.connectors.jdbc.sink.v2;

import org.apache.flink.cdc.connectors.jdbc.internal.data.JdbcRowData;
import java.io.IOException;
import java.io.Serializable;

/**
 * Abstract base serialization schema class between generic type {@code T} and {@link JdbcRowData}.
 */
public interface RecordSerializationSchema<IN> extends Serializable {
    JdbcRowData[] serialize(IN record) throws IOException;
}
