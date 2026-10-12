package io.yak.ops.flow.runtime.transformations;

import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.dag.Transformation;
import java.util.List;
import java.util.Objects;

/**
 * Logical Source node with no upstream transformations.
 *
 * <p>Stores the reusable Source definition without opening a connection, creating
 * a Reader or starting a task. Execution owns the actual runtime resources.
 *
 * @param <T> the emitted record type
 * @author weifuwan
 */
public final class SourceTransformation<T> extends Transformation<T> {

    /** Reusable Source definition, not an initialized Reader. */
    private final Source<T, ?, ?> source;

    public SourceTransformation(String name, Source<T, ?, ?> source, Class<T> outputType) {
        this(name, source, outputType, DEFAULT_PARALLELISM);
    }

    public SourceTransformation(String name, Source<T, ?, ?> source, Class<T> outputType, int parallelism) {
        super(name, outputType, parallelism);
        this.source = Objects.requireNonNull(source, "source must not be null");
    }

    /** Returns the Source definition without opening runtime resources. */
    public Source<T, ?, ?> getSource() {
        return source;
    }

    /** Returns the Source boundedness, independently of the selected runtime mode. */
    public Boundedness getBoundedness() {
        return Objects.requireNonNull(source.getBoundedness(), "Source must declare its boundedness");
    }

    /** Returns no inputs because a Source is an upstream root. */
    @Override
    public List<Transformation<?>> getInputs() {
        return List.of();
    }
}
