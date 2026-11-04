package org.apache.flink.cdc.common.udf;

import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.types.DataType;

/**
 * Base interface for creating a UDF in transform projection and filtering expressions. You should
 * define at least one {@code eval} method.
 *
 * <p>When {@code transform.async-execution.enabled} is enabled, the same UDF instance may be
 * invoked concurrently. Implementations used in asynchronous transforms must therefore be
 * thread-safe.
 */
@PublicEvolving
public interface UserDefinedFunction {

    /**
     * Returns the return type of this UDF when it cannot be derived from per-UDF options.
     *
     * @deprecated Use {@link #getReturnType(UserDefinedFunctionContext)} instead.
     */
    @Deprecated
    default DataType getReturnType() {
        return null;
    }

    /** Returns the return type of this UDF, given the per-UDF YAML options. */
    default DataType getReturnType(UserDefinedFunctionContext context) {
        return getReturnType();
    }

    /**
     * This will be invoked every time when a UDF got created.
     *
     * @deprecated Use {@link #open(UserDefinedFunctionContext)} instead.
     */
    @Deprecated
    default void open() throws Exception {}

    /** This will be invoked every time when a UDF got created. */
    default void open(UserDefinedFunctionContext context) throws Exception {
        open();
    }

    /** This will be invoked before a UDF got destroyed. */
    default void close() throws Exception {}
}
