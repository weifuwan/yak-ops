package org.apache.flink.cdc.debezium.table;

import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.table.data.RowData;

/** A converter converts {@link RowData} metadata into Flink internal data structures. */
@Internal
public interface RowDataMetadataConverter extends MetadataConverter {

    Object read(RowData rowData);
}
