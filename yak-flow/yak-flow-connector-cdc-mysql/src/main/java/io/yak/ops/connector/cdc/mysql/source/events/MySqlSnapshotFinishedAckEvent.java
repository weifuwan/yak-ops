package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.core.api.connector.source.SourceEvent;

/** Allows a Reader to drop an already coordinator-acknowledged completed split. */
public record MySqlSnapshotFinishedAckEvent(String splitId) implements SourceEvent {}
