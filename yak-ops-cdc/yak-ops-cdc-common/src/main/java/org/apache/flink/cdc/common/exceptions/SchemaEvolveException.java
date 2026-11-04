package org.apache.flink.cdc.common.exceptions;

import org.apache.flink.cdc.common.event.SchemaChangeEvent;
import org.apache.flink.util.FlinkRuntimeException;

import javax.annotation.Nullable;

/** An exception occurred during schema evolution. */
public class SchemaEvolveException extends FlinkRuntimeException {
    protected final SchemaChangeEvent applyingEvent;
    protected final String exceptionMessage;
    protected final @Nullable Throwable cause;

    public SchemaEvolveException(SchemaChangeEvent applyingEvent, String exceptionMessage) {
        this(applyingEvent, exceptionMessage, null);
    }

    public SchemaEvolveException(
            SchemaChangeEvent applyingEvent, String exceptionMessage, @Nullable Throwable cause) {
        super(cause);
        this.applyingEvent = applyingEvent;
        this.exceptionMessage = exceptionMessage;
        this.cause = cause;
    }

    public SchemaChangeEvent getApplyingEvent() {
        return applyingEvent;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    @Nullable
    public Throwable getCause() {
        return cause;
    }

    @Override
    public String toString() {
        return "SchemaEvolveException{"
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
