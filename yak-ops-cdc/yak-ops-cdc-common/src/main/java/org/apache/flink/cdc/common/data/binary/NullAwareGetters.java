package org.apache.flink.cdc.common.data.binary;

import org.apache.flink.cdc.common.annotation.Internal;

/** Provides null related getters. */
@Internal
public interface NullAwareGetters {

    /** If no field is null, return false. Returns true if one of the columns is null. */
    boolean anyNull();

    /**
     * For the input fields, if no field is null, return false. Returns true if one of the columns
     * is null.
     */
    boolean anyNull(int[] fields);
}
