package org.apache.flink.cdc.common.exceptions;

import org.apache.flink.cdc.common.event.SchemaChangeEvent;

import javax.annotation.Nullable;

/** A special kind of {@link SchemaEvolveException} that sink doesn't support such event type. */
public class UnsupportedSchemaChangeEventException extends SchemaEvolveException {

    public UnsupportedSchemaChangeEventException(SchemaChangeEvent applyingEvent) {
        this(applyingEvent, "Sink doesn't support such schema change event.");
    }

    public UnsupportedSchemaChangeEventException(
            SchemaChangeEvent applyingEvent, String exceptionMessage) {
        this(applyingEvent, exceptionMessage, null);
    }

    public UnsupportedSchemaChangeEventException(
            SchemaChangeEvent applyingEvent, String exceptionMessage, @Nullable Throwable cause) {
        super(applyingEvent, exceptionMessage, cause);
    }

    @Override
    public String toString() {
        return "UnsupportedSchemaChangeEventException{"
                + "applyingEvent="
                + applyingEvent
                + ", exceptionMessage='"
                + exceptionMessage
                + '\''
                + ", cause='"
                + cause
                + '\''
                + '}';
    }
}
