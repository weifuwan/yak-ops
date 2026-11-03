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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.runtime.tasks.LocalExecutionPlan;
import io.yak.ops.cdc.runtime.tasks.StreamTask;
import io.yak.ops.cdc.runtime.transformations.OneInputTransformation;
import io.yak.ops.cdc.runtime.transformations.SinkTransformation;
import io.yak.ops.cdc.runtime.transformations.SourceTransformation;
import io.yak.ops.cdc.runtime.transformations.Transformation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StreamGraphGeneratorTest {

    @Test
    void recursivelyTranslatesInputsAndKeepsOperatorsLazy() throws Exception {
        AtomicInteger created = new AtomicInteger();
        List<Integer> values = new ArrayList<>();
        SourceTransformation<Integer> source = new SourceTransformation<>(
                11, "source", () -> {
                    created.incrementAndGet();
                    return output -> output.collect(2);
                });
        source.setUid("src-uid");
        OneInputTransformation<Integer, Integer> first = new OneInputTransformation<>(
                15, "increment", source, () -> {
                    created.incrementAndGet();
                    return (input, output) -> output.collect(input + 3);
                });
        first.setUid("increment-uid");
        OneInputTransformation<Integer, Integer> second = new OneInputTransformation<>(
                19, "multiply", first, () -> {
                    created.incrementAndGet();
                    return (input, output) -> output.collect(input * 4);
                });
        SinkTransformation<Integer> sink = new SinkTransformation<>(
                29, "sink", second, () -> {
                    created.incrementAndGet();
                    return values::add;
                });
        sink.setUid("sink-uid");

        // The generator must traverse references, not expect registration-order nodes.
        StreamGraphGenerator generator = new StreamGraphGenerator(
                List.<Transformation<?>>of(sink, second, source, first, sink));
        StreamGraph graph = generator.generate();
        assertTrue(graph.isSealed());
        assertEquals(0, created.get());
        assertEquals(List.of(11, 15, 19, 29),
                graph.getNodes().stream().map(StreamNode::getId).toList());
        assertEquals(List.of(11, 15, 19),
                graph.getEdges().stream().map(StreamEdge::getSourceId).toList());
        assertEquals(List.of(15, 19, 29),
                graph.getEdges().stream().map(StreamEdge::getTargetId).toList());
        assertEquals("src-uid", graph.getStreamNode(11).getTransformationUID());
        assertEquals("increment-uid", graph.getStreamNode(15).getTransformationUID());
        assertEquals("sink-uid", graph.getStreamNode(29).getTransformationUID());
        assertEquals(1, graph.getStreamNode(15).getInEdges().size());
        assertEquals(1, graph.getStreamNode(15).getOutEdges().size());
        assertEquals(List.of(first), second.getInputs());
        assertEquals(List.of(), source.getInputs());

        StreamGraph regenerated = generator.generate();
        assertNotSame(graph, regenerated);
        assertEquals(4, regenerated.getNodes().size());
        assertEquals(0, created.get());

        new StreamTask(LocalExecutionPlan.compile(graph)).execute();
        assertEquals(List.of(20), values);
        assertEquals(4, created.get());
    }

    @Test
    void sharedLogicalInputIsTranslatedOnceAndBranchingRemainsGraphCapability() {
        SourceTransformation<Integer> source = new SourceTransformation<>(
                1, "source", () -> output -> {});
        OneInputTransformation<Integer, Integer> left = new OneInputTransformation<>(
                2, "left", source, () -> (value, output) -> {});
        OneInputTransformation<Integer, Integer> right = new OneInputTransformation<>(
                3, "right", source, () -> (value, output) -> {});
        SinkTransformation<Integer> leftSink = new SinkTransformation<>(
                4, "leftSink", left, () -> value -> {});
        SinkTransformation<Integer> rightSink = new SinkTransformation<>(
                5, "rightSink", right, () -> value -> {});

        StreamGraph graph = new StreamGraphGenerator(
                List.<Transformation<?>>of(rightSink, leftSink)).generate();
        assertEquals(5, graph.getNodes().size());
        assertEquals(4, graph.getEdges().size());
        assertEquals(2, graph.getStreamNode(1).getOutEdges().size());
        assertEquals(1, graph.getSourceIDs().size());
        assertEquals(2, graph.getSinkIDs().size());
        assertThrows(IllegalArgumentException.class, () -> LocalExecutionPlan.compile(graph));
    }

    @Test
    void rejectsDuplicateIdsAndUidsWithoutInstantiatingOperators() {
        AtomicInteger created = new AtomicInteger();
        SourceTransformation<Integer> source = new SourceTransformation<>(
                1, "source", () -> {
                    created.incrementAndGet();
                    return output -> {};
                });
        OneInputTransformation<Integer, Integer> duplicate = new OneInputTransformation<>(
                1, "duplicate-id", source, () -> (value, output) -> {});
        SinkTransformation<Integer> sink = new SinkTransformation<>(
                3, "sink", duplicate, () -> value -> {});
        assertThrows(IllegalArgumentException.class,
                () -> new StreamGraphGenerator(List.<Transformation<?>>of(sink)).generate());

        OneInputTransformation<Integer, Integer> valid = new OneInputTransformation<>(
                2, "transform", source, () -> (value, output) -> {});
        SinkTransformation<Integer> otherSink = new SinkTransformation<>(
                3, "sink", valid, () -> value -> {});
        source.setUid("same");
        valid.setUid("same");
        assertThrows(IllegalArgumentException.class,
                () -> new StreamGraphGenerator(
                        List.<Transformation<?>>of(otherSink)).generate());
        assertEquals(0, created.get());
    }
}
