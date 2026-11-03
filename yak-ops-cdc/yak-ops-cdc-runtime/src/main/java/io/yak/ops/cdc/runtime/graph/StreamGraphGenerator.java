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

import io.yak.ops.cdc.runtime.transformations.OneInputTransformation;
import io.yak.ops.cdc.runtime.transformations.SinkTransformation;
import io.yak.ops.cdc.runtime.transformations.SourceTransformation;
import io.yak.ops.cdc.runtime.transformations.Transformation;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds a fresh StreamGraph by recursively translating logical transformations.
 *
 * <p>Mirrors the essential Flink StreamGraphGenerator boundary: translation happens once per
 * transformation identity, inputs are visited before their consumers, and operator factories
 * remain unopened. Distributed translators, partitioning and checkpointing are intentionally
 * outside the standalone runtime.
 */
public final class StreamGraphGenerator {
    private final List<Transformation<?>> transformations;

    public StreamGraphGenerator(List<Transformation<?>> transformations) {
        this.transformations = List.copyOf(Objects.requireNonNull(transformations, "transformations"));
    }

    public StreamGraph generate() {
        StreamGraph graph = new StreamGraph();
        Map<Transformation<?>, Integer> translated = new IdentityHashMap<>();
        Set<Transformation<?>> visiting =
                Collections.newSetFromMap(new IdentityHashMap<Transformation<?>, Boolean>());

        for (Transformation<?> transformation : transformations) {
            transform(Objects.requireNonNull(transformation, "transformation"),
                    graph, translated, visiting);
        }
        graph.seal();
        return graph;
    }

    private int transform(
            Transformation<?> transformation,
            StreamGraph graph,
            Map<Transformation<?>, Integer> translated,
            Set<Transformation<?>> visiting) {
        Integer previous = translated.get(transformation);
        if (previous != null) {
            return previous;
        }
        if (!visiting.add(transformation)) {
            throw new IllegalArgumentException(
                    "Cycle in logical transformations at " + transformation.getName());
        }

        try {
            List<Transformation<?>> inputs = transformation.getInputs();
            if (transformation instanceof SourceTransformation<?>) {
                if (!inputs.isEmpty()) {
                    throw new IllegalArgumentException("A source cannot have inputs");
                }
            } else if (transformation instanceof OneInputTransformation<?, ?>
                    || transformation instanceof SinkTransformation<?>) {
                if (inputs.size() != 1) {
                    throw new IllegalArgumentException(
                            "One-input transformation requires exactly one input: "
                                    + transformation.getName());
                }
            } else {
                throw new UnsupportedOperationException(
                        "Unsupported local transformation: "
                                + transformation.getClass().getName());
            }

            for (Transformation<?> input : inputs) {
                transform(input, graph, translated, visiting);
            }

            StreamNode node = new StreamNode(
                    transformation.getId(),
                    transformation.getName(),
                    transformation.getUid(),
                    transformation.getOperatorType(),
                    transformation.getOperatorFactory());
            if (transformation instanceof SourceTransformation<?>) {
                graph.addSource(node);
            } else if (transformation instanceof SinkTransformation<?>) {
                graph.addSink(node);
            } else {
                graph.addOperator(node);
            }

            for (Transformation<?> input : inputs) {
                graph.addEdge(input.getId(), transformation.getId());
            }
            translated.put(transformation, node.getId());
            return node.getId();
        } finally {
            visiting.remove(transformation);
        }
    }
}
