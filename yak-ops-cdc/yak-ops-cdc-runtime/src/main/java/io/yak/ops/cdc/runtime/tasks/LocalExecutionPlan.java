/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.cdc.runtime.tasks;

import io.yak.ops.cdc.runtime.graph.OperatorType;
import io.yak.ops.cdc.runtime.graph.StreamEdge;
import io.yak.ops.cdc.runtime.graph.StreamGraph;
import io.yak.ops.cdc.runtime.graph.StreamNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable local execution topology compiled from the directed edges of a StreamGraph.
 *
 * <p>The current single-threaded runner only supports one source, one sink and zero or more
 * one-input transforms. Validation here deliberately does not limit the StreamGraph model.
 * No operator is constructed during compilation.
 */
public final class LocalExecutionPlan {
    private final List<StreamNode> nodes;
    private final List<StreamEdge> edges;
    private final int sourceId;
    private final int sinkId;

    private LocalExecutionPlan(
            List<StreamNode> nodes, List<StreamEdge> edges, int sourceId, int sinkId) {
        this.nodes = List.copyOf(nodes);
        this.edges = List.copyOf(edges);
        this.sourceId = sourceId;
        this.sinkId = sinkId;
    }

    public static LocalExecutionPlan compile(StreamGraph graph) {
        Objects.requireNonNull(graph, "graph");
        if (graph.getSourceIDs().size() != 1 || graph.getSinkIDs().size() != 1) {
            throw new IllegalArgumentException(
                    "Local pipeline requires exactly one source and one sink");
        }

        int sourceId = graph.getSourceIDs().iterator().next();
        int sinkId = graph.getSinkIDs().iterator().next();
        List<StreamNode> nodes = new ArrayList<>();
        List<StreamEdge> edges = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        StreamNode current = requireNode(graph, sourceId);

        while (true) {
            if (!visited.add(current.getId())) {
                throw new IllegalArgumentException(
                        "Cycle detected in local pipeline at node " + current.getId());
            }

            boolean isSource = current.getId() == sourceId;
            boolean isSink = current.getId() == sinkId;
            int inputCount = current.getInEdges().size();
            int outputCount = current.getOutEdges().size();

            if (isSource) {
                if (current.getType() != OperatorType.SOURCE
                        || inputCount != 0 || outputCount != 1) {
                    throw new IllegalArgumentException(
                            "Local source requires no input and exactly one output: " + current);
                }
            } else if (isSink) {
                if (current.getType() != OperatorType.SINK
                        || inputCount != 1 || outputCount != 0) {
                    throw new IllegalArgumentException(
                            "Local sink requires exactly one input and no output: " + current);
                }
            } else if (current.getType() != OperatorType.TRANSFORM
                    || inputCount != 1 || outputCount != 1) {
                throw new IllegalArgumentException(
                        "Local transforms require exactly one input and one output: " + current);
            }

            nodes.add(current);
            if (isSink) {
                break;
            }

            StreamEdge edge = current.getOutEdges().get(0);
            if (edge.getSourceId() != current.getId() || edge.getTypeNumber() != 0) {
                throw new IllegalArgumentException(
                        "Unsupported local edge or input number: " + edge);
            }
            StreamNode downstream = requireNode(graph, edge.getTargetId());
            if (downstream.getInEdges().size() != 1
                    || !downstream.getInEdges().contains(edge)) {
                throw new IllegalArgumentException(
                        "Local pipeline does not support multiple inputs: " + downstream);
            }
            edges.add(edge);
            current = downstream;
        }

        // An independent path or disconnected node cannot be silently omitted by the runner.
        if (nodes.size() != graph.getNodes().size()
                || edges.size() != graph.getEdges().size()) {
            throw new IllegalArgumentException(
                    "Local pipeline must be a connected source-to-sink path");
        }

        return new LocalExecutionPlan(nodes, edges, sourceId, sinkId);
    }

    private static StreamNode requireNode(StreamGraph graph, int nodeId) {
        StreamNode node = graph.getStreamNode(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Unknown stream node: " + nodeId);
        }
        return node;
    }

    /** Node order derived by following edges from source, not registration or graph sort order. */
    public List<StreamNode> getNodes() {
        return nodes;
    }

    /** The ordered source-to-sink edges, used to wire the actual operator outputs. */
    public List<StreamEdge> getEdges() {
        return edges;
    }

    public int getSourceId() {
        return sourceId;
    }

    public int getSinkId() {
        return sinkId;
    }
}
