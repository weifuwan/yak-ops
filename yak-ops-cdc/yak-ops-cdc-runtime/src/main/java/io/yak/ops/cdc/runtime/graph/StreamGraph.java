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

package io.yak.ops.cdc.runtime.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Local stream topology. Owns node registration and maintains both sides of every edge.
 *
 * <p>The graph can represent a directed acyclic topology. Single-threaded execution constraints
 * are validated when a local execution plan is built, not by this graph.
 *
 * <p>Once sealed, topology and operator UIDs cannot be modified. Operators remain uninstantiated.
 */
public final class StreamGraph {
    private final Map<Integer, StreamNode> streamNodes = new LinkedHashMap<>();
    private final Set<Integer> sources = new LinkedHashSet<>();
    private final Set<Integer> sinks = new LinkedHashSet<>();
    private final List<StreamEdge> edges = new ArrayList<>();

    private boolean sealed;
    private List<StreamNode> orderedNodes;

    public void addSource(StreamNode node) {
        checkType(node, OperatorType.SOURCE);
        addNode(node);
    }

    public void addOperator(StreamNode node) {
        checkType(node, OperatorType.TRANSFORM);
        addNode(node);
    }

    public void addSink(StreamNode node) {
        checkType(node, OperatorType.SINK);
        addNode(node);
    }

    public void addNode(StreamNode node) {
        checkMutable();
        Objects.requireNonNull(node, "node");
        if (streamNodes.containsKey(node.getId())) {
            throw new IllegalArgumentException("Duplicate node id: " + node.getId());
        }
        if (!node.getInEdges().isEmpty() || !node.getOutEdges().isEmpty()) {
            throw new IllegalArgumentException("Cannot register a node already connected to a graph");
        }
        ensureUniqueUID(node.getId(), node.getTransformationUID());
        streamNodes.put(node.getId(), node);
        if (node.getType() == OperatorType.SOURCE) {
            sources.add(node.getId());
        } else if (node.getType() == OperatorType.SINK) {
            sinks.add(node.getId());
        }
    }

    public void addEdge(int sourceId, int targetId) {
        addEdge(sourceId, targetId, 0);
    }

    public void addEdge(int sourceId, int targetId, int typeNumber) {
        checkMutable();
        StreamNode source = requireNode(sourceId);
        StreamNode target = requireNode(targetId);
        if (source.getType() == OperatorType.SINK || target.getType() == OperatorType.SOURCE) {
            throw new IllegalArgumentException("Invalid edge direction: " + source + " -> " + target);
        }
        int uniqueId = 0;
        for (StreamEdge existing : edges) {
            if (existing.getSourceId() == sourceId
                    && existing.getTargetId() == targetId
                    && existing.getTypeNumber() == typeNumber) {
                uniqueId++;
            }
        }
        StreamEdge edge = new StreamEdge(source, target, typeNumber, uniqueId);
        source.addOutEdge(edge);
        target.addInEdge(edge);
        edges.add(edge);
    }

    public void setTransformationUID(int nodeId, String uid) {
        checkMutable();
        StreamNode node = requireNode(nodeId);
        if (uid != null && uid.isBlank()) {
            throw new IllegalArgumentException("UID must not be blank");
        }
        ensureUniqueUID(nodeId, uid);
        node.setTransformationUID(uid);
    }

    public StreamNode getStreamNode(int id) {
        return streamNodes.get(id);
    }

    public Collection<StreamNode> getStreamNodes() {
        return getNodes();
    }

    public List<StreamNode> getNodes() {
        return Collections.unmodifiableList(new ArrayList<>(streamNodes.values()));
    }

    public List<StreamEdge> getEdges() {
        return Collections.unmodifiableList(new ArrayList<>(edges));
    }

    public List<StreamEdge> getStreamEdges(int sourceId) {
        return getStreamEdges(sourceId, null);
    }

    public List<StreamEdge> getStreamEdges(int sourceId, int targetId) {
        return getStreamEdges(sourceId, Integer.valueOf(targetId));
    }

    private List<StreamEdge> getStreamEdges(int sourceId, Integer targetId) {
        List<StreamEdge> matches = new ArrayList<>();
        for (StreamEdge edge : edges) {
            if (edge.getSourceId() == sourceId
                    && (targetId == null || edge.getTargetId() == targetId.intValue())) {
                matches.add(edge);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    public Set<Integer> getSourceIDs() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(sources));
    }

    public Set<Integer> getSinkIDs() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(sinks));
    }

    public boolean isSealed() {
        return sealed;
    }

    /** Make the graph read-only only after a complete topological validation. */
    public void seal() {
        if (!sealed) {
            List<StreamNode> order = topologicalOrder();
            orderedNodes = order;
            sealed = true;
        }
    }

    /** All nodes in topological order, including possible branches. */
    public List<StreamNode> getOrderedNodes() {
        return sealed ? orderedNodes : topologicalOrder();
    }

    private List<StreamNode> topologicalOrder() {
        Map<Integer, Integer> inDegrees = new LinkedHashMap<>();
        ArrayDeque<StreamNode> available = new ArrayDeque<>();
        for (StreamNode node : streamNodes.values()) {
            int degree = node.getInEdges().size();
            inDegrees.put(node.getId(), degree);
            if (degree == 0) {
                available.add(node);
            }
        }

        List<StreamNode> result = new ArrayList<>(streamNodes.size());
        while (!available.isEmpty()) {
            StreamNode current = available.remove();
            result.add(current);
            for (StreamEdge edge : current.getOutEdges()) {
                int targetId = edge.getTargetId();
                int remaining = inDegrees.get(targetId) - 1;
                inDegrees.put(targetId, remaining);
                if (remaining == 0) {
                    available.add(requireNode(targetId));
                }
            }
        }
        if (result.size() != streamNodes.size()) {
            throw new IllegalArgumentException("Cycle detected in pipeline");
        }
        return Collections.unmodifiableList(result);
    }

    private void ensureUniqueUID(int nodeId, String uid) {
        if (uid == null) {
            return;
        }
        for (StreamNode node : streamNodes.values()) {
            if (node.getId() != nodeId && uid.equals(node.getTransformationUID())) {
                throw new IllegalArgumentException("Duplicate operator UID: " + uid);
            }
        }
    }

    private StreamNode requireNode(int id) {
        StreamNode node = streamNodes.get(id);
        if (node == null) {
            throw new IllegalArgumentException("Unknown stream node: " + id);
        }
        return node;
    }

    private static void checkType(StreamNode node, OperatorType expected) {
        Objects.requireNonNull(node, "node");
        if (node.getType() != expected) {
            throw new IllegalArgumentException("Expected " + expected + " node: " + node);
        }
    }

    private void checkMutable() {
        if (sealed) {
            throw new IllegalStateException("The pipeline graph is already finalized");
        }
    }
}
