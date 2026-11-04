package org.apache.flink.cdc.common.source;

import org.apache.flink.api.connector.source.Source;
import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

/**
 * A marker interface used to provide an event source for reading events from external systems. We
 * can reuse exiting Flink {@link Source} and Flink {@link SourceFunction} implementation, and we
 * can support our own {@code EventSource} implementation in the future.
 */
@PublicEvolving
public interface EventSourceProvider {}
