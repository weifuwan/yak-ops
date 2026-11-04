package org.apache.flink.cdc.common.source.discover;

import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.event.TableId;

/**
 * Table-specific specialization of {@link ObjectIdDiscoverer} whose discovered object id type is
 * {@link TableId}.
 */
@PublicEvolving
public interface TableDiscoverer extends ObjectIdDiscoverer<TableId> {}
