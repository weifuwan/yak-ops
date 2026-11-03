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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.runtime.graph.OperatorType;
import io.yak.ops.cdc.runtime.graph.StreamEdge;
import io.yak.ops.cdc.runtime.graph.StreamGraph;
import io.yak.ops.cdc.runtime.graph.StreamNode;
import io.yak.ops.cdc.runtime.operators.OneInputStreamOperator;
import io.yak.ops.cdc.runtime.operators.Output;
import io.yak.ops.cdc.runtime.operators.SinkOperator;
import io.yak.ops.cdc.runtime.operators.SourceOperator;
import io.yak.ops.cdc.runtime.operators.StreamOperator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LocalExecutionPlanTest {

    @Test
    void compilesAndExecutesFromEdgesRegardlessOfNodeAndEdgeRegistrationOrder() throws Exception {
        List<Integer> received = new ArrayList<>();
        List<String> lifecycle = new ArrayList<>();
        AtomicInteger constructions = new AtomicInteger();
        StreamGraph graph = new StreamGraph();

        // Intentionally register nodes and edges in reverse order, with nonsequential IDs.
        graph.addSink(new StreamNode(40, "sink", OperatorType.SINK, () -> {
            constructions.incrementAndGet();
            return new SinkOperator<Integer>() {
                @Override
                public void open() { lifecycle.add("open-sink"); }
                @Override
                public void write(Integer value) { received.add(value); }
                @Override
                public void close() { lifecycle.add("close-sink"); }
            };
        }));
        graph.addOperator(new StreamNode(30, "second", OperatorType.TRANSFORM, () -> {
            constructions.incrementAndGet();
            return new OneInputStreamOperator<Integer, Integer>() {
                @Override
                public void open() { lifecycle.add("open-second"); }
                @Override
                public void processElement(Integer value, Output<Integer> output)
                        throws Exception {
                    output.collect(value * 10);
                }
                @Override
                public void close() { lifecycle.add("close-second"); }
            };
        }));
        graph.addSource(new StreamNode(10, "source", OperatorType.SOURCE, () -> {
            constructions.incrementAndGet();
            return new SourceOperator<Integer>() {
                @Override
                public void open() { lifecycle.add("open-source"); }
                @Override
                public void run(Output<Integer> output) throws Exception {
                    output.collect(1);
                    output.collect(2);
                }
                @Override
                public void close() { lifecycle.add("close-source"); }
            };
        }));
        graph.addOperator(new StreamNode(20, "first", OperatorType.TRANSFORM, () -> {
            constructions.incrementAndGet();
            return new OneInputStreamOperator<Integer, Integer>() {
                @Override
                public void open() { lifecycle.add("open-first"); }
                @Override
                public void processElement(Integer value, Output<Integer> output)
                        throws Exception {
                    output.collect(value + 1);
                }
                @Override
                public void close() { lifecycle.add("close-first"); }
            };
        }));
        graph.addEdge(30, 40);
        graph.addEdge(20, 30);
        graph.addEdge(10, 20);

        LocalExecutionPlan plan = LocalExecutionPlan.compile(graph);
        assertEquals(0, constructions.get(), "Compilation must not create operators");
        assertEquals(List.of(10, 20, 30, 40),
                plan.getNodes().stream().map(StreamNode::getId).toList());
        assertEquals(List.of(10, 20, 30),
                plan.getEdges().stream().map(StreamEdge::getSourceId).toList());
        assertEquals(List.of(20, 30, 40),
                plan.getEdges().stream().map(StreamEdge::getTargetId).toList());
        assertEquals(10, plan.getSourceId());
        assertEquals(40, plan.getSinkId());
        assertThrows(UnsupportedOperationException.class, () -> plan.getEdges().clear());

        StreamTask task = new StreamTask(plan);
        task.execute();
        assertEquals(4, constructions.get());
        assertEquals(List.of(20, 30), received);
        assertEquals(List.of("open-sink", "open-second", "open-first", "open-source",
                "close-source", "close-first", "close-second", "close-sink"), lifecycle);
        assertThrows(IllegalStateException.class, task::execute);
    }

    @Test
    void directSourceToSinkDoesNotRequireTransformOrSpecialCaseRouting() throws Exception {
        List<String> received = new ArrayList<>();
        StreamGraph graph = new StreamGraph();
        graph.addSink(new StreamNode(8, "sink", OperatorType.SINK,
                () -> (SinkOperator<String>) received::add));
        graph.addSource(new StreamNode(4, "source", OperatorType.SOURCE,
                () -> (SourceOperator<String>) output -> output.collect("event")));
        graph.addEdge(4, 8);

        LocalExecutionPlan plan = LocalExecutionPlan.compile(graph);
        assertEquals(1, plan.getEdges().size());
        new StreamTask(plan).execute();
        assertEquals(List.of("event"), received);
    }

    @Test
    void rejectsUnconnectedBranchMultiInputAndNonzeroInputNumberBeforeCreatingOperators() {
        AtomicInteger created = new AtomicInteger();
        StreamGraph branch = new StreamGraph();
        branch.addSource(node(1, OperatorType.SOURCE, created));
        branch.addOperator(node(2, OperatorType.TRANSFORM, created));
        branch.addOperator(node(3, OperatorType.TRANSFORM, created));
        branch.addSink(node(4, OperatorType.SINK, created));
        branch.addEdge(1, 2);
        branch.addEdge(1, 3);
        branch.addEdge(2, 4);
        branch.addEdge(3, 4);
        assertThrows(IllegalArgumentException.class, () -> LocalExecutionPlan.compile(branch));
        assertEquals(0, created.get());

        StreamGraph disconnected = new StreamGraph();
        disconnected.addSource(node(1, OperatorType.SOURCE, created));
        disconnected.addOperator(node(2, OperatorType.TRANSFORM, created));
        disconnected.addSink(node(3, OperatorType.SINK, created));
        disconnected.addEdge(1, 3);
        assertThrows(IllegalArgumentException.class,
                () -> LocalExecutionPlan.compile(disconnected));

        StreamGraph inputNumber = new StreamGraph();
        inputNumber.addSource(node(1, OperatorType.SOURCE, created));
        inputNumber.addSink(node(2, OperatorType.SINK, created));
        inputNumber.addEdge(1, 2, 1);
        assertThrows(IllegalArgumentException.class,
                () -> LocalExecutionPlan.compile(inputNumber));
        assertEquals(0, created.get());
    }

    @Test
    void rejectsWrongOperatorTypeBeforeOpeningButClosesInstances() {
        AtomicInteger closed = new AtomicInteger();
        StreamGraph graph = new StreamGraph();
        graph.addSource(new StreamNode(1, "source", OperatorType.SOURCE,
                () -> new SourceOperator<Integer>() {
                    @Override public void run(Output<Integer> output) {}
                    @Override public void close() { closed.incrementAndGet(); }
                }));
        graph.addSink(new StreamNode(2, "sink", OperatorType.SINK,
                () -> new StreamOperator() {
                    @Override public void close() { closed.incrementAndGet(); }
                }));
        graph.addEdge(1, 2);
        StreamTask task = new StreamTask(LocalExecutionPlan.compile(graph));
        assertThrows(IllegalStateException.class, task::execute);
        assertEquals(2, closed.get());
    }

    @Test
    void cancellationBeforeExecutionDoesNotCreateOperators() throws Exception {
        AtomicInteger created = new AtomicInteger();
        StreamGraph graph = new StreamGraph();
        graph.addSource(node(1, OperatorType.SOURCE, created));
        graph.addSink(node(2, OperatorType.SINK, created));
        graph.addEdge(1, 2);
        StreamTask task = new StreamTask(LocalExecutionPlan.compile(graph));
        task.cancel();
        task.execute();
        assertTrue(task.isCancelled());
        assertEquals(0, created.get());
        assertThrows(IllegalStateException.class, task::execute);
    }

    private static StreamNode node(int id, OperatorType type, AtomicInteger created) {
        return new StreamNode(id, type.name().toLowerCase(), type, () -> {
            created.incrementAndGet();
            return new StreamOperator() {};
        });
    }
}
