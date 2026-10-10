package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.core.api.connector.source.SourceEvent;

/** Reports a snapshot split only after its final row has left the Reader mailbox. */
public record MySqlSnapshotFinishedEvent(String splitId) implements SourceEvent {}
