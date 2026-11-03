/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.cdc.runtime.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validated immutable logical graph for one source, any number of transforms, and one sink.
 *
 * <p>The first standalone runtime intentionally rejects fan-out, fan-in and disconnected nodes;
 * such graph topologies need an explicit execution design before they can be enabled.
 */
public final class StreamGraph {
    private final List<StreamNode> nodes;
    private final List<StreamEdge> edges;
    private final List<StreamNode> orderedNodes;

    public StreamGraph(List<StreamNode> nodes, List<StreamEdge> edges) {
        this.nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        this.edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
        this.orderedNodes = validateAndOrder();
    }

    public List<StreamNode> getNodes() {
        return nodes;
    }

    public List<StreamEdge> getEdges() {
        return edges;
    }

    /** Nodes in the only valid execution order: source -> transforms -> sink. */
    public List<StreamNode> getOrderedNodes() {
        return orderedNodes;
    }

    private List<StreamNode> validateAndOrder() {
        Map<Integer, StreamNode> byId = new LinkedHashMap<>();
        Set<String> uids = new HashSet<>();
        StreamNode source = null;
        int sinks = 0;
        for (StreamNode node : nodes) {
            if (byId.putIfAbsent(node.id(), node) != null) {
                throw new IllegalArgumentException("Duplicate node id: " + node.id());
            }
            if (node.uid() != null && !uids.add(node.uid())) {
                throw new IllegalArgumentException("Duplicate operator UID: " + node.uid());
            }
            if (node.type() == OperatorType.SOURCE) {
                if (source != null) {
                    throw new IllegalArgumentException("Exactly one source is supported");
                }
                source = node;
            }
            if (node.type() == OperatorType.SINK) {
                sinks++;
            }
        }
        if (source == null || sinks != 1) {
            throw new IllegalArgumentException("Pipeline requires exactly one source and one sink");
        }

        Map<Integer, Integer> successors = new HashMap<>();
        Map<Integer, Integer> predecessors = new HashMap<>();
        for (StreamEdge edge : edges) {
            StreamNode from = byId.get(edge.sourceId());
            StreamNode to = byId.get(edge.targetId());
            if (from == null || to == null) {
                throw new IllegalArgumentException("Edge references missing node: " + edge);
            }
            if (from.type() == OperatorType.SINK || to.type() == OperatorType.SOURCE) {
                throw new IllegalArgumentException("Invalid edge direction: " + edge);
            }
            if (successors.putIfAbsent(edge.sourceId(), edge.targetId()) != null
                    || predecessors.putIfAbsent(edge.targetId(), edge.sourceId()) != null) {
                throw new IllegalArgumentException("Branches and multiple inputs are not yet supported");
            }
        }

        List<StreamNode> result = new ArrayList<>(nodes.size());
        Set<Integer> visited = new HashSet<>();
        int cursor = source.id();
        while (true) {
            if (!visited.add(cursor)) {
                throw new IllegalArgumentException("Cycle detected in pipeline");
            }
            StreamNode current = byId.get(cursor);
            result.add(current);
            Integer next = successors.get(cursor);
            if (next == null) {
                if (current.type() != OperatorType.SINK) {
                    throw new IllegalArgumentException("Pipeline does not terminate in a sink");
                }
                break;
            }
            cursor = next;
        }
        if (result.size() != nodes.size() || predecessors.containsKey(source.id())) {
            throw new IllegalArgumentException("Disconnected or invalid nodes in pipeline");
        }
        return List.copyOf(result);
    }
}
