package io.yak.ops.flow.runtime.jobgraph;

import io.yak.ops.flow.runtime.graph.StreamEdge;
import java.util.Objects;

/** A physical connection between two deployable JobVertices. */
public record JobEdge(int sourceVertexId, int targetVertexId, StreamEdge streamEdge) {

    public JobEdge {
        if (sourceVertexId <= 0 || targetVertexId <= 0 || sourceVertexId == targetVertexId) {
            throw new IllegalArgumentException("Invalid physical JobEdge endpoints");
        }
        Objects.requireNonNull(streamEdge, "streamEdge");
    }
}
