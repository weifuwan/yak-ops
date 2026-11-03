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

import io.yak.ops.cdc.runtime.operators.OperatorFactory;
import io.yak.ops.cdc.runtime.operators.StreamOperator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A logical stream node with its incoming and outgoing edges.
 *
 * <p>Operators are created by the factory only when execution starts. Graph construction never
 * creates an operator instance.
 */
public final class StreamNode {
    private final int id;
    private final String operatorName;
    private final OperatorType type;
    private final OperatorFactory<? extends StreamOperator> operatorFactory;
    private final List<StreamEdge> inEdges = new ArrayList<>();
    private final List<StreamEdge> outEdges = new ArrayList<>();
    private String transformationUID;

    public StreamNode(
            int id,
            String operatorName,
            OperatorType type,
            OperatorFactory<? extends StreamOperator> operatorFactory) {
        this(id, operatorName, null, type, operatorFactory);
    }

    public StreamNode(
            int id,
            String operatorName,
            String transformationUID,
            OperatorType type,
            OperatorFactory<? extends StreamOperator> operatorFactory) {
        if (id < 1) {
            throw new IllegalArgumentException("Node id must be positive");
        }
        if (operatorName == null || operatorName.isBlank()) {
            throw new IllegalArgumentException("Node name must not be blank");
        }
        if (transformationUID != null && transformationUID.isBlank()) {
            throw new IllegalArgumentException("UID must not be blank");
        }
        this.id = id;
        this.operatorName = operatorName;
        this.transformationUID = transformationUID;
        this.type = Objects.requireNonNull(type, "type");
        this.operatorFactory = Objects.requireNonNull(operatorFactory, "operatorFactory");
    }

    public int getId() {
        return id;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public String getTransformationUID() {
        return transformationUID;
    }

    public OperatorType getType() {
        return type;
    }

    public OperatorFactory<? extends StreamOperator> getOperatorFactory() {
        return operatorFactory;
    }

    public List<StreamEdge> getInEdges() {
        return Collections.unmodifiableList(inEdges);
    }

    public List<StreamEdge> getOutEdges() {
        return Collections.unmodifiableList(outEdges);
    }

    void setTransformationUID(String uid) {
        if (uid != null && uid.isBlank()) {
            throw new IllegalArgumentException("UID must not be blank");
        }
        this.transformationUID = uid;
    }

    void addInEdge(StreamEdge edge) {
        Objects.requireNonNull(edge, "edge");
        if (edge.getTargetId() != id || inEdges.contains(edge)) {
            throw new IllegalArgumentException("Invalid or duplicate incoming edge: " + edge);
        }
        inEdges.add(edge);
    }

    void addOutEdge(StreamEdge edge) {
        Objects.requireNonNull(edge, "edge");
        if (edge.getSourceId() != id || outEdges.contains(edge)) {
            throw new IllegalArgumentException("Invalid or duplicate outgoing edge: " + edge);
        }
        outEdges.add(edge);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StreamNode)) {
            return false;
        }
        StreamNode that = (StreamNode) other;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return operatorName + "-" + id;
    }
}
