package org.apache.flink.cdc.common.data;

import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.types.CharType;
import org.apache.flink.cdc.common.types.VarCharType;

/** An internal data structure representing data of {@link CharType} and {@link VarCharType}. */
@PublicEvolving
public interface StringData extends Comparable<StringData> {

    /**
     * Converts this {@link StringData} object to a UTF-8 byte array.
     *
     * <p>Note: The returned byte array may be reused.
     */
    byte[] toBytes();

    /** Converts this {@link StringData} object to a {@link String}. */
    String toString();
}
