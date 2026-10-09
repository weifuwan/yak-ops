package io.yak.ops.core.api.dag;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Logical transformation node of a data-processing pipeline.
 *
 * <p>A Transformation describes an operator and its direct dependencies; it does not
 * execute records or own threads and connections. Names, parallelism and stable UIDs are
 * configured before compiling the physical graph and must not be modified afterward.
 *
 * @param <T> the record type produced by this transformation
 * @author weifuwan
 */
public abstract class Transformation<T> {

    /** Sentinel indicating that the execution configuration supplies the resolved parallelism. */
    public static final int DEFAULT_PARALLELISM = -1;

    private static final AtomicInteger ID_COUNTER = new AtomicInteger();

    /** JVM-local graph ID, not a stable identity for checkpoint recovery. */
    private final int id;

    /** Operator name used for topology display and diagnostics. */
    private String name;

    /** Java output type; field-level schemas are described separately. */
    private final Class<T> outputType;

    /** Declared parallelism, or {@link #DEFAULT_PARALLELISM} when inherited. */
    private int parallelism;

    /** Stable user-defined operator UID used to match checkpoint state across executions. */
    private String uid;

    protected Transformation(String name, Class<T> outputType) {
        this(name, outputType, DEFAULT_PARALLELISM);
    }

    protected Transformation(String name, Class<T> outputType, int parallelism) {
        this.id = ID_COUNTER.incrementAndGet();
        this.outputType = Objects.requireNonNull(outputType, "outputType 不能为空");
        setName(name);
        setParallelism(parallelism);
    }

    /** Returns the graph-local identifier; it must not be used as a checkpoint UID. */
    public final int getId() {
        return id;
    }

    /** Returns the operator name used for display and diagnostics. */
    public final String getName() {
        return name;
    }

    /** Sets the name before physical graph compilation. */
    public final void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        this.name = name;
    }

    /** Returns the Java type produced by this node. */
    public final Class<T> getOutputType() {
        return outputType;
    }

    /** Returns the declared parallelism, or {@link #DEFAULT_PARALLELISM} if inherited. */
    public final int getParallelism() {
        return parallelism;
    }

    /** Sets an explicit positive parallelism or {@link #DEFAULT_PARALLELISM}. */
    public final void setParallelism(int parallelism) {
        if (parallelism != DEFAULT_PARALLELISM && parallelism <= 0) {
            throw new IllegalArgumentException("parallelism 必须为 -1 或正整数");
        }
        this.parallelism = parallelism;
    }

    /** Returns the stable operator UID, or null if it has not been assigned. */
    public final String getUid() {
        return uid;
    }

    /** Sets a UID that must be unique within the pipeline for stateful recovery. */
    public final void setUid(String uid) {
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("uid 不能为空");
        }
        this.uid = uid;
    }

    /**
     * Returns the direct upstream transformations.
     *
     * <p>Sources return an empty list; single-input nodes return one entry. The returned list
     * must be immutable and must never be null.
     *
     * @return immutable direct upstream dependencies
     */
    public abstract List<Transformation<?>> getInputs();
}
