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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.runtime.operators.StreamOperator;
import io.yak.ops.cdc.runtime.tasks.LocalExecutionPlan;
import io.yak.ops.cdc.runtime.tasks.StreamTask;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreamGraphTest {

    @Test
    void registersNodesAndMaintainsBothSidesOfEveryEdge() {
        StreamGraph graph = new StreamGraph();
        StreamNode source = node(1, OperatorType.SOURCE);
        StreamNode transform = node(2, OperatorType.TRANSFORM);
        StreamNode sink = node(3, OperatorType.SINK);
        graph.addSource(source);
        graph.addOperator(transform);
        graph.addSink(sink);
        graph.addEdge(1, 2);
        graph.addEdge(2, 3);

        assertEquals(List.of(1), List.copyOf(graph.getSourceIDs()));
        assertEquals(List.of(3), List.copyOf(graph.getSinkIDs()));
        assertEquals(List.of(source, transform, sink), LocalExecutionPlan.compile(graph).getNodes());
        assertEquals(1, source.getOutEdges().size());
        assertEquals(1, transform.getInEdges().size());
        assertEquals(1, transform.getOutEdges().size());
        assertEquals(1, sink.getInEdges().size());
        assertEquals(source.getOutEdges().get(0), transform.getInEdges().get(0));
        assertEquals(transform.getOutEdges().get(0), sink.getInEdges().get(0));
        assertEquals(0, source.getOutEdges().get(0).getTypeNumber());
        assertThrows(UnsupportedOperationException.class, () -> source.getOutEdges().clear());

        graph.setTransformationUID(2, "transform-uid");
        assertEquals("transform-uid", graph.getStreamNode(2).getTransformationUID());
        graph.seal();
        assertTrue(graph.isSealed());
        assertThrows(IllegalStateException.class, () -> graph.addEdge(1, 3));
        assertThrows(IllegalStateException.class, () -> graph.setTransformationUID(2, "other"));
        assertThrows(UnsupportedOperationException.class, () -> graph.getNodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.getEdges().clear());
    }

    @Test
    void distinguishesParallelLogicalEdgesAndKeepsTopologyGeneral() {
        StreamGraph graph = new StreamGraph();
        StreamNode source = node(1, OperatorType.SOURCE);
        StreamNode sink = node(2, OperatorType.SINK);
        graph.addSource(source);
        graph.addSink(sink);
        graph.addEdge(1, 2, 0);
        graph.addEdge(1, 2, 0);
        graph.addEdge(1, 2, 1);

        assertEquals(3, graph.getStreamEdges(1, 2).size());
        assertEquals(3, source.getOutEdges().size());
        assertEquals(3, sink.getInEdges().size());
        assertEquals(0, graph.getEdges().get(0).getUniqueId());
        assertEquals(1, graph.getEdges().get(1).getUniqueId());
        assertEquals(0, graph.getEdges().get(2).getUniqueId());
        assertNotEquals(graph.getEdges().get(0), graph.getEdges().get(1));
        assertNotEquals(graph.getEdges().get(0), graph.getEdges().get(2));
        assertEquals(List.of(source, sink), graph.getOrderedNodes());
        assertThrows(IllegalArgumentException.class, () -> LocalExecutionPlan.compile(graph));
    }

    @Test
    void allowsGraphBranchingButLocalTaskRejectsIt() {
        StreamGraph graph = new StreamGraph();
        graph.addSource(node(1, OperatorType.SOURCE));
        graph.addOperator(node(2, OperatorType.TRANSFORM));
        graph.addOperator(node(3, OperatorType.TRANSFORM));
        graph.addSink(node(4, OperatorType.SINK));
        graph.addEdge(1, 2);
        graph.addEdge(1, 3);
        graph.addEdge(2, 4);
        graph.addEdge(3, 4);

        assertEquals(2, graph.getStreamNode(1).getOutEdges().size());
        assertEquals(2, graph.getStreamNode(4).getInEdges().size());
        graph.seal();
        assertEquals(4, graph.getOrderedNodes().size());
        assertThrows(IllegalArgumentException.class, () -> LocalExecutionPlan.compile(graph));
        assertThrows(IllegalArgumentException.class, () -> new StreamTask(graph).execute());
    }

    @Test
    void rejectsDuplicateIdsUidsInvalidDirectionsAndCycles() {
        StreamGraph graph = new StreamGraph();
        graph.addSource(new StreamNode(1, "source", "source-uid", OperatorType.SOURCE,
                () -> new StreamOperator() {}));
        graph.addOperator(node(2, OperatorType.TRANSFORM));
        graph.addSink(node(3, OperatorType.SINK));

        assertThrows(IllegalArgumentException.class,
                () -> graph.addOperator(node(1, OperatorType.TRANSFORM)));
        assertThrows(IllegalArgumentException.class,
                () -> graph.addOperator(new StreamNode(4, "duplicated", "source-uid",
                        OperatorType.TRANSFORM, () -> new StreamOperator() {})));
        assertThrows(IllegalArgumentException.class, () -> graph.addEdge(3, 2));
        assertThrows(IllegalArgumentException.class, () -> graph.addEdge(2, 1));
        assertThrows(IllegalArgumentException.class, () -> graph.addEdge(2, 999));
        assertThrows(IllegalArgumentException.class, () -> graph.addEdge(2, 2));
        assertEquals(3, graph.getNodes().size());

        StreamNode sameId = node(2, OperatorType.TRANSFORM);
        assertEquals(graph.getStreamNode(2), sameId);
        assertEquals(graph.getStreamNode(2).hashCode(), sameId.hashCode());

        graph.addEdge(1, 2);
        graph.addEdge(2, 3);
        graph.addEdge(2, 2 + 1, 1);
        assertEquals(2, graph.getStreamNode(2).getOutEdges().size());

        StreamGraph cycle = new StreamGraph();
        cycle.addSource(node(1, OperatorType.SOURCE));
        cycle.addOperator(node(2, OperatorType.TRANSFORM));
        cycle.addOperator(node(3, OperatorType.TRANSFORM));
        cycle.addSink(node(4, OperatorType.SINK));
        cycle.addEdge(1, 2);
        cycle.addEdge(2, 3);
        cycle.addEdge(3, 2);
        cycle.addEdge(3, 4);
        assertThrows(IllegalArgumentException.class, cycle::seal);
        assertTrue(!cycle.isSealed());
    }

    private static StreamNode node(int id, OperatorType type) {
        return new StreamNode(id, type.name().toLowerCase(),
                type, () -> new StreamOperator() {});
    }
}
