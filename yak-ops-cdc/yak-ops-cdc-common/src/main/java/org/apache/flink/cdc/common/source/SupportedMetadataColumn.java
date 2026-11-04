package org.apache.flink.cdc.common.source;

import org.apache.flink.cdc.common.annotation.Experimental;
import org.apache.flink.cdc.common.event.DataChangeEvent;
import org.apache.flink.cdc.common.types.DataType;

import java.io.Serializable;
import java.util.Map;

/** A metadata column that the source supports to read from the meta field. */
@Experimental
public interface SupportedMetadataColumn extends Serializable {
    /** Column name. */
    String getName();

    /** The data type of this column in Flink CDC. */
    DataType getType();

    /** The returned java class of the reader. */
    Class<?> getJavaClass();

    /**
     * Read the metadata from the {@link DataChangeEvent#meta()}.
     *
     * @param metadata the metadata returned from {@link DataChangeEvent#meta()}
     * @return the value of this metadata found by metadata name
     */
    Object read(Map<String, String> metadata);
}
