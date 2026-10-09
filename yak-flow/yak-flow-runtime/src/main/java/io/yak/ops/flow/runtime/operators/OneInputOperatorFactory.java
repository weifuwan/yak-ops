package io.yak.ops.flow.runtime.operators;

/**
 * Reusable factory for creating independent single-input operator instances.
 *
 * <p>Each job attempt and parallel subtask receives a distinct operator to avoid sharing
 * mutable state. The factory does not own runtime resources; stateful operators implement
 * {@link CheckpointedStreamOperator} and use the runtime's state backend.
 *
 * @param <IN> the input record type
 * @param <OUT> the output record type
 * @author weifuwan
 */
@FunctionalInterface
public interface OneInputOperatorFactory<IN, OUT> {

    /**
 * Creates one operator instance for a task attempt.
 *
 * @return a new, non-null operator owned by the runtime
 * @throws Exception if creation fails
 */
    OneInputStreamOperator<IN, OUT> createOperator() throws Exception;
}
