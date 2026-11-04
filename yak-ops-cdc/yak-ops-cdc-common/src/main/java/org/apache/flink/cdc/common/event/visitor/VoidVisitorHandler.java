package org.apache.flink.cdc.common.event.visitor;

import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.cdc.common.event.SchemaChangeEvent;
import org.apache.flink.cdc.common.event.TruncateTableEvent;

/** Visitor for {@link TruncateTableEvent}s. */
@Internal
@FunctionalInterface
public interface VoidVisitorHandler<EVT extends SchemaChangeEvent, E extends Throwable> {
    void visit(EVT event) throws E;
}
