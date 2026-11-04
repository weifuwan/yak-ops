package org.apache.flink.cdc.debezium.table;

import org.apache.flink.cdc.common.annotation.Internal;

import org.apache.kafka.connect.source.SourceRecord;

import java.io.Serializable;

/** A converter converts {@link SourceRecord} metadata into Flink internal data structures. */
@FunctionalInterface
@Internal
public interface MetadataConverter extends Serializable {

    Object read(SourceRecord record);
}
