package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.core.api.connector.source.SourceEvent;

/** Releases idle snapshot I/O after the completed handoff without ending an unbounded SourceReader. */
public record MySqlStopSnapshotEvent() implements SourceEvent {}
