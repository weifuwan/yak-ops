package io.yak.ops.connector.cdc.mysql.source.enumerator;

/**
 * Marker for the mode-specific MySQL split assignment state.
 *
 * <p>Snapshot handoff and standalone Binlog assignment retain separate serialized formats.
 */
public sealed interface PendingSplitsState permits MySqlHybridEnumeratorState, MySqlPendingSplitsState {}
