package org.apache.flink.cdc.runtime.operators.sink.exception;

import org.apache.flink.cdc.common.event.Event;

/**
 * A generic {@link RuntimeException} thrown when an error occurs in {@link
 * org.apache.flink.cdc.runtime.operators.sink.DataSinkWriterOperator} and {@link
 * org.apache.flink.cdc.runtime.operators.sink.DataSinkFunctionOperator}.
 */
public class SinkWrapperException extends RuntimeException {
    public SinkWrapperException(Event event, Throwable cause) {
        super(String.format("Failed to handle event %s in DataSink wrapper.", event), cause);
    }
}
