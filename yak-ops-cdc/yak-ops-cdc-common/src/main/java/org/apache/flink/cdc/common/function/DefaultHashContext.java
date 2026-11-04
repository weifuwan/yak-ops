package org.apache.flink.cdc.common.function;

import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.cdc.common.utils.Preconditions;

/** Default implementation of {@link HashFunction.HashContext}. */
@Internal
public final class DefaultHashContext implements HashFunction.HashContext {

    private final int sourceSubtaskIndex;
    private final int downstreamParallelism;

    public DefaultHashContext(int sourceSubtaskIndex, int downstreamParallelism) {
        Preconditions.checkArgument(
                sourceSubtaskIndex >= 0,
                "sourceSubtaskIndex must be greater than or equal to 0, but was %s.",
                sourceSubtaskIndex);
        Preconditions.checkArgument(
                downstreamParallelism > 0,
                "downstreamParallelism must be greater than 0, but was %s.",
                downstreamParallelism);
        this.sourceSubtaskIndex = sourceSubtaskIndex;
        this.downstreamParallelism = downstreamParallelism;
    }

    @Override
    public int getSourceSubtaskIndex() {
        return sourceSubtaskIndex;
    }

    @Override
    public int getDownstreamParallelism() {
        return downstreamParallelism;
    }
}
