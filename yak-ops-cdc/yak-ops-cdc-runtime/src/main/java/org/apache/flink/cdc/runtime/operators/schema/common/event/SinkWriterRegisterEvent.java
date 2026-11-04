package org.apache.flink.cdc.runtime.operators.schema.common.event;

import org.apache.flink.cdc.runtime.operators.schema.common.SchemaRegistry;
import org.apache.flink.runtime.operators.coordination.OperatorEvent;

import java.util.Objects;

/** A {@link OperatorEvent} that register sink writer to {@link SchemaRegistry}. */
public class SinkWriterRegisterEvent implements OperatorEvent {

    private static final long serialVersionUID = 1L;

    private final int subtask;

    public SinkWriterRegisterEvent(int subtask) {
        this.subtask = subtask;
    }

    public int getSubtask() {
        return subtask;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SinkWriterRegisterEvent)) {
            return false;
        }
        SinkWriterRegisterEvent that = (SinkWriterRegisterEvent) o;
        return subtask == that.subtask;
    }

    @Override
    public int hashCode() {
        return Objects.hash(subtask);
    }
}
