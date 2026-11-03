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

package io.yak.ops.cdc.runtime.datastream;

import io.yak.ops.cdc.runtime.graph.OperatorType;
import io.yak.ops.cdc.runtime.graph.StreamEdge;
import io.yak.ops.cdc.runtime.graph.StreamGraph;
import io.yak.ops.cdc.runtime.graph.StreamNode;
import io.yak.ops.cdc.runtime.operators.OneInputStreamOperator;
import io.yak.ops.cdc.runtime.operators.OperatorFactory;
import io.yak.ops.cdc.runtime.operators.SinkOperator;
import io.yak.ops.cdc.runtime.operators.SourceOperator;
import io.yak.ops.cdc.runtime.tasks.StreamTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal local DataStream builder. Builds a graph, not live operators.
 *
 * <p>A single environment represents exactly one linear pipeline. Once materialized as a graph,
 * its topology is sealed and can be executed synchronously or asynchronously.
 */
public final class StreamExecutionEnvironment {
    private final Map<Integer, StreamNode> nodes = new LinkedHashMap<>();
    private final List<StreamEdge> edges = new ArrayList<>();
    private int nextId = 1;
    private boolean sealed;
    private boolean submitted;
    private StreamGraph graph;

    public static StreamExecutionEnvironment getLocalEnvironment() {
        return new StreamExecutionEnvironment();
    }

    public <T> DataStream<T> fromSource(
            String name, OperatorFactory<? extends SourceOperator<T>> factory) {
        checkMutable();
        if (!nodes.isEmpty()) {
            throw new IllegalStateException("Only one source per pipeline is supported");
        }
        int id = addNode(name, OperatorType.SOURCE, factory);
        return new DataStream<>(this, id);
    }

    <IN, OUT> DataStream<OUT> addTransform(
            int inputId, String name,
            OperatorFactory<? extends OneInputStreamOperator<IN, OUT>> factory) {
        validateTail(inputId);
        int id = addNode(name, OperatorType.TRANSFORM, factory);
        edges.add(new StreamEdge(inputId, id));
        return new DataStream<>(this, id);
    }

    <T> void addSink(
            int inputId, String name,
            OperatorFactory<? extends SinkOperator<T>> factory, String uid) {
        validateTail(inputId);
        int id = addNode(name, OperatorType.SINK, factory);
        if (uid != null) {
            setUid(id, uid);
        }
        edges.add(new StreamEdge(inputId, id));
    }

    void setUid(int id, String uid) {
        checkMutable();
        StreamNode node = Objects.requireNonNull(nodes.get(id), "Unknown node: " + id);
        if (uid != null) {
            for (StreamNode existing : nodes.values()) {
                if (existing.id() != id && uid.equals(existing.uid())) {
                    throw new IllegalArgumentException("Duplicate operator UID: " + uid);
                }
            }
        }
        nodes.put(id, node.withUid(uid));
    }

    private int addNode(
            String name, OperatorType type,
            OperatorFactory<? extends io.yak.ops.cdc.runtime.operators.StreamOperator> factory) {
        checkMutable();
        int id = nextId++;
        nodes.put(id, new StreamNode(id, name, null, type, factory));
        return id;
    }

    private void validateTail(int inputId) {
        checkMutable();
        StreamNode input = nodes.get(inputId);
        if (input == null) {
            throw new IllegalArgumentException("Unknown upstream node: " + inputId);
        }
        if (input.type() == OperatorType.SINK || nodes.values().stream()
                .anyMatch(n -> n.type() == OperatorType.SINK)) {
            throw new IllegalStateException("Cannot attach nodes after the sink");
        }
        if (edges.stream().anyMatch(e -> e.sourceId() == inputId)) {
            throw new IllegalArgumentException("Branching is not supported in the first runtime");
        }
    }

    private void checkMutable() {
        if (sealed) {
            throw new IllegalStateException("The pipeline graph is already finalized");
        }
    }

    public StreamGraph getStreamGraph() {
        if (graph == null) {
            graph = new StreamGraph(new ArrayList<>(nodes.values()), edges);
            sealed = true;
        }
        return graph;
    }

    public void execute(String jobName) throws Exception {
        Objects.requireNonNull(jobName, "jobName");
        synchronized (this) {
            if (submitted) {
                throw new IllegalStateException("Pipeline was already submitted");
            }
            StreamGraph snapshot = getStreamGraph();
            submitted = true;
            new StreamTask(snapshot).execute();
        }
    }

    public StreamExecutionHandle executeAsync(String jobName) {
        Objects.requireNonNull(jobName, "jobName");
        final StreamGraph snapshot;
        synchronized (this) {
            if (submitted) {
                throw new IllegalStateException("Pipeline was already submitted");
            }
            snapshot = getStreamGraph();
            submitted = true;
        }
        StreamTask task = new StreamTask(snapshot);
        CompletableFuture<Void> completion = new CompletableFuture<>();
        String id = UUID.randomUUID().toString();
        Thread runner = new Thread(() -> {
            try {
                task.execute();
                completion.complete(null);
            } catch (Throwable failure) {
                completion.completeExceptionally(failure);
            }
        }, "yak-cdc-" + id);
        runner.setDaemon(true);
        StreamExecutionHandle handle =
                new StreamExecutionHandle(id, jobName, completion, task, runner);
        runner.start();
        return handle;
    }
}
