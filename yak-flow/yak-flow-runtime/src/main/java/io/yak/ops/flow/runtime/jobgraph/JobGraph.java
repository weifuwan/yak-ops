package io.yak.ops.flow.runtime.jobgraph;

import io.yak.ops.core.api.RuntimeExecutionMode;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable physical execution plan. JobVertex represents a deployable operator chain; JobEdge
 * records only cross-task connections, not edges internal to a chain.
 */
public final class JobGraph {

    private final JobID jobID;
    private final StreamGraph sourceGraph;
    private final RuntimeExecutionMode runtimeMode;
    private final Configuration configuration;
    private final List<JobVertex> vertices;
    private final List<JobEdge> edges;

    public JobGraph(
            JobID jobID,
            StreamGraph sourceGraph,
            RuntimeExecutionMode runtimeMode,
            Configuration configuration,
            List<JobVertex> vertices,
            List<JobEdge> edges) {
        this.jobID = Objects.requireNonNull(jobID, "jobID");
        this.sourceGraph = Objects.requireNonNull(sourceGraph, "sourceGraph");
        this.runtimeMode = Objects.requireNonNull(runtimeMode, "runtimeMode");
        this.configuration = new Configuration(Objects.requireNonNull(configuration, "configuration"));
        this.vertices = List.copyOf(Objects.requireNonNull(vertices, "vertices"));
        this.edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
        if (this.vertices.isEmpty() || this.edges.size() != this.vertices.size() - 1) {
            throw new IllegalArgumentException("Only physical linear JobGraphs are supported");
        }
        Set<Integer> ids = new HashSet<>();
        Set<Integer> logicalNodes = new HashSet<>();
        for (JobVertex vertex : this.vertices) {
            if (!ids.add(vertex.getId())) {
                throw new IllegalArgumentException("Duplicate JobVertex id: " + vertex.getId());
            }
            for (var node : vertex.getOperators()) {
                if (!logicalNodes.add(node.getId())) {
                    throw new IllegalArgumentException("StreamNode appears in multiple JobVertices: " + node.getId());
                }
            }
        }
        if (logicalNodes.size() != sourceGraph.getStreamNodes().size()) {
            throw new IllegalArgumentException("Physical JobGraph must cover all StreamNodes");
        }
        for (int i = 0; i < this.edges.size(); i++) {
            JobEdge edge = this.edges.get(i);
            JobVertex upstream = this.vertices.get(i);
            JobVertex downstream = this.vertices.get(i + 1);
            if (edge.sourceVertexId() != upstream.getId()
                    || edge.targetVertexId() != downstream.getId()
                    || edge.streamEdge().sourceId() != upstream.getTailOperator().getId()
                    || edge.streamEdge().targetId() != downstream.getHeadOperator().getId()) {
                throw new IllegalArgumentException("Invalid physical JobGraph edge ordering");
            }
        }
    }

    public JobID jobID() {
        return jobID;
    }

    /** Logical graph retained solely for stable checkpoint signature and boundedness. */
    public StreamGraph graph() {
        return sourceGraph;
    }

    public boolean isBounded() {
        return sourceGraph.isBounded();
    }

    public RuntimeExecutionMode runtimeMode() {
        return runtimeMode;
    }

    public Configuration configuration() {
        return new Configuration(configuration);
    }

    public List<JobVertex> getVertices() {
        return vertices;
    }

    public List<JobEdge> getEdges() {
        return edges;
    }

    public boolean isSingleChainedVertex() {
        return vertices.size() == 1 && vertices.getFirst().isChained();
    }
}
