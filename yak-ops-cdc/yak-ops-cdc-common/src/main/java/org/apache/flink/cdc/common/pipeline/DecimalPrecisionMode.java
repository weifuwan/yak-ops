package org.apache.flink.cdc.common.pipeline;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

/**
 * Maximum precision mode for DECIMAL type in transform expressions. Controls the upper bound of
 * numeric precision used by the SQL type system during expression evaluation.
 */
@PublicEvolving
public enum DecimalPrecisionMode {

    /** Limits DECIMAL precision to 19 digits, matching Calcite's default type system behavior. */
    UP_TO_19,

    /** Allows DECIMAL precision up to 38 digits, matching Flink CDC's extended type system. */
    UP_TO_38
}
