package org.apache.flink.cdc.common.sink;

import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

/**
 * A marker interface used to provide an event sink for writing change events to external systems.
 * We can reuse exiting Flink {@link Sink} and Flink {@link SinkFunction} implementation, and we can
 * support our own {@code EventSink} Implementation in the future.
 */
@PublicEvolving
public interface EventSinkProvider {}
