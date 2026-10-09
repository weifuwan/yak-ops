package io.yak.ops.core.api.connector.source;

/**
 * Describes whether a Source can produce a finite or continuously unbounded stream.
 *
 * <p>Boundedness is a property of the Source, not the resolved execution mode.
 */
public enum Boundedness {
    /** Produces a finite stream, such as a complete table snapshot. */
    BOUNDED,

    /** Produces an unbounded stream, such as a continuous CDC subscription. */
    CONTINUOUS_UNBOUNDED
}
