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

import java.io.Serializable;
import java.util.Objects;

/**
 * A directed connection between two logical operator nodes.
 *
 * <p>The input number and per-pair unique id distinguish connections independently of any
 * network partitioning or distributed exchange mode.
 */
public final class StreamEdge implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String edgeId;
    private final int sourceId;
    private final int targetId;
    private final int typeNumber;
    private final int uniqueId;
    private final String sourceOperatorName;
    private final String targetOperatorName;

    public StreamEdge(StreamNode source, StreamNode target, int typeNumber, int uniqueId) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (source.getId() == target.getId()) {
            throw new IllegalArgumentException("Self-loop is not supported: " + source.getId());
        }
        if (typeNumber < 0 || uniqueId < 0) {
            throw new IllegalArgumentException("Edge input number and unique id must be nonnegative");
        }
        this.sourceId = source.getId();
        this.targetId = target.getId();
        this.typeNumber = typeNumber;
        this.uniqueId = uniqueId;
        this.sourceOperatorName = source.getOperatorName();
        this.targetOperatorName = target.getOperatorName();
        this.edgeId = sourceId + "_" + targetId + "_" + typeNumber + "_" + uniqueId;
    }

    public String getEdgeId() {
        return edgeId;
    }

    public int getSourceId() {
        return sourceId;
    }

    public int getTargetId() {
        return targetId;
    }

    public int getTypeNumber() {
        return typeNumber;
    }

    public int getUniqueId() {
        return uniqueId;
    }

    public String getSourceOperatorName() {
        return sourceOperatorName;
    }

    public String getTargetOperatorName() {
        return targetOperatorName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StreamEdge)) {
            return false;
        }
        StreamEdge that = (StreamEdge) other;
        return edgeId.equals(that.edgeId);
    }

    @Override
    public int hashCode() {
        return edgeId.hashCode();
    }

    @Override
    public String toString() {
        return "(" + sourceOperatorName + "-" + sourceId
                + " -> " + targetOperatorName + "-" + targetId
                + ", typeNumber=" + typeNumber + ", uniqueId=" + uniqueId + ")";
    }
}
