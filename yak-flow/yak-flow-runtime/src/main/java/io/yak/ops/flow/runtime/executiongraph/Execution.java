package io.yak.ops.flow.runtime.executiongraph;

import io.yak.ops.flow.runtime.tasks.StreamTask;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * One deployment attempt of an ExecutionVertex. The initial embedded engine only creates attempt 0;
 * a retry must allocate a new Execution instead of restarting the old StreamTask.
 */
public final class Execution implements AutoCloseable {

    private final ExecutionVertex vertex;
    private final int attemptNumber;
    private volatile ExecutionState state = ExecutionState.CREATED;
    private StreamTask task;

    Execution(ExecutionVertex vertex, int attemptNumber) {
        this.vertex = Objects.requireNonNull(vertex, "vertex");
        if (attemptNumber < 0) {
            throw new IllegalArgumentException("attemptNumber cannot be negative");
        }
        this.attemptNumber = attemptNumber;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public ExecutionState getState() {
        return state;
    }

    public ExecutionVertex getVertex() {
        return vertex;
    }

    void deploy(StreamTask task) {
        Objects.requireNonNull(task, "task");
        synchronized (this) {
            if (this.task != null || state != ExecutionState.CREATED) {
                throw new IllegalStateException("An Execution may only be deployed once");
            }
            if (task.taskInfo().subtaskIndex() != vertex.getSubtaskIndex()
                    || task.taskInfo().operatorId() != vertex.getJobVertex().getJobVertex().getId()
                    || task.taskInfo().attemptNumber() != attemptNumber) {
                throw new IllegalArgumentException("StreamTask identity does not match its ExecutionVertex");
            }
            this.task = task;
            state = ExecutionState.DEPLOYING;
        }
        task.completionFuture().whenComplete((unused, failure) -> {
            synchronized (Execution.this) {
                if (failure == null) {
                    state = ExecutionState.FINISHED;
                } else if (failure instanceof CancellationException) {
                    state = ExecutionState.CANCELED;
                } else {
                    // An error while cancelling (including failed cleanup) remains a failed attempt.
                    state = ExecutionState.FAILED;
                }
            }
        });
    }

    CompletableFuture<Void> start() {
        StreamTask current;
        synchronized (this) {
            if (state != ExecutionState.DEPLOYING || task == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Execution is not deployable"));
            }
            current = task;
        }
        return current.start().whenComplete((unused, failure) -> {
            synchronized (Execution.this) {
                if (failure == null && state == ExecutionState.DEPLOYING) {
                    state = ExecutionState.RUNNING;
                }
            }
        });
    }

    CompletableFuture<Void> completionFuture() {
        StreamTask current;
        synchronized (this) {
            current = task;
        }
        return current == null ? CompletableFuture.failedFuture(
                new IllegalStateException("Execution has not been deployed")) : current.completionFuture();
    }

    CompletableFuture<Void> cancelAsync() {
        StreamTask current;
        synchronized (this) {
            if (state == ExecutionState.FINISHED || state == ExecutionState.FAILED
                    || state == ExecutionState.CANCELED) {
                return CompletableFuture.completedFuture(null);
            }
            state = ExecutionState.CANCELLING;
            current = task;
            if (current == null) {
                state = ExecutionState.CANCELED;
                return CompletableFuture.completedFuture(null);
            }
        }
        return current.cancelAsync();
    }

    @Override
    public void close() throws Exception {
        StreamTask current;
        synchronized (this) {
            current = task;
        }
        if (current != null) {
            current.close();
        }
    }
}
