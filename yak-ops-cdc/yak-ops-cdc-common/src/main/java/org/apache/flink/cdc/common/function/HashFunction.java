package org.apache.flink.cdc.common.function;

import org.apache.flink.cdc.common.annotation.Internal;

/**
 * The hash function used to calculate the hash code from a given event.
 *
 * @param <T> the type of given event.
 */
@Internal
public interface HashFunction<T> {

    /** Runtime context for calculating an event hash code. */
    @Internal
    interface HashContext {
        int getSourceSubtaskIndex();

        int getDownstreamParallelism();
    }

    /** Creates a hash context. */
    static HashContext createContext(int sourceSubtaskIndex, int downstreamParallelism) {
        return new DefaultHashContext(sourceSubtaskIndex, downstreamParallelism);
    }

    int hashcode(T event);

    /**
     * Calculates the hash code with the runtime hash context.
     *
     * <p>Implementations that do not depend on the context can continue implementing {@link
     * #hashcode(Object)} only.
     */
    default int hashcode(HashContext context, T event) {
        return hashcode(event);
    }
}
