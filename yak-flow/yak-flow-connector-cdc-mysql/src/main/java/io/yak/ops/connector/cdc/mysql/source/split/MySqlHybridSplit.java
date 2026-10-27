package io.yak.ops.connector.cdc.mysql.source.split;

/**
 * Checkpointable unit of hybrid CDC work: a finite JDBC snapshot range or the unbounded Binlog.
 *
 * <p>The two kinds have deliberately distinct progress and recovery semantics.
 */
public sealed interface MySqlHybridSplit extends MySqlSplit permits MySqlSnapshotSplit, MySqlHybridBinlogSplit {}
