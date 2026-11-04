package io.yak.ops.server.master.engine.exceptions;

import io.yak.ops.server.master.engine.AbstractLifecycleEvent;

public class WorkflowEventFireException extends RuntimeException {

    public WorkflowEventFireException(AbstractLifecycleEvent event, Throwable cause) {
        super("Failed to fire event: " + event, cause);
    }
}
