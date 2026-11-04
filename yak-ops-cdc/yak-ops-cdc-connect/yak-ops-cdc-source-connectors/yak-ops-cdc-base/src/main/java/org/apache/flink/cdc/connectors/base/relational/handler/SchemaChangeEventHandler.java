package org.apache.flink.cdc.connectors.base.relational.handler;

import org.apache.flink.cdc.common.annotation.Experimental;

import io.debezium.schema.SchemaChangeEvent;

import java.util.Map;

/** This handler helps to parse the source struct in SchemaChangeEvent and generate source info. */
@Experimental
public interface SchemaChangeEventHandler {

    Map<String, Object> parseSource(SchemaChangeEvent event);
}
