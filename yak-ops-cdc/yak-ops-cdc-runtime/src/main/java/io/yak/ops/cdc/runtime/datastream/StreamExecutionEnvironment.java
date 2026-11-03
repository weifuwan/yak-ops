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

package io.yak.ops.cdc.runtime.datastream;

import io.yak.ops.cdc.runtime.graph.OperatorType;
import io.yak.ops.cdc.runtime.graph.StreamGraph;
import io.yak.ops.cdc.runtime.graph.StreamNode;
import io.yak.ops.cdc.runtime.operators.OneInputStreamOperator;
import io.yak.ops.cdc.runtime.operators.OperatorFactory;
import io.yak.ops.cdc.runtime.operators.SinkOperator;
import io.yak.ops.cdc.runtime.operators.SourceOperator;
import io.yak.ops.cdc.runtime.operators.StreamOperator;
import io.yak.ops.cdc.runtime.tasks.LocalExecutionPlan;
import io.yak.ops.cdc.runtime.tasks.StreamTask;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Local DataStream builder. Delegates topology management to StreamGraph, not live operators.
 *
 * <p>For now an environment builds a single linear pipeline, while StreamGraph can represent
 * more general acyclic topologies. Once finalized, the graph cannot be modified.
 */
public final class StreamExecutionEnvironment {
    private final StreamGraph graph = new StreamGraph();
    private int nextId = 1;
    private boolean submitted;
    private LocalExecutionPlan executionPlan;

    public static StreamExecutionEnvironment getLocalEnvironment() {
        return new StreamExecutionEnvironment();
    }

    public <T> DataStream<T> fromSource(
            String name, OperatorFactory<? extends SourceOperator<T>> factory) {
        checkMutable();
        if (!graph.getNodes().isEmpty()) {
            throw new IllegalStateException("Only one source per pipeline is supported");
        }
        int id = addNode(name, OperatorType.SOURCE, factory, null);
        return new DataStream<>(this, id);
    }

    <IN, OUT> DataStream<OUT> addTransform(
            int inputId, String name,
            OperatorFactory<? extends OneInputStreamOperator<IN, OUT>> factory) {
        validateTail(inputId);
        int id = addNode(name, OperatorType.TRANSFORM, factory, null);
        graph.addEdge(inputId, id);
        return new DataStream<>(this, id);
    }

    <T> void addSink(
            int inputId, String name,
            OperatorFactory<? extends SinkOperator<T>> factory, String uid) {
        validateTail(inputId);
        int id = addNode(name, OperatorType.SINK, factory, uid);
        graph.addEdge(inputId, id);
    }

    void setUid(int id, String uid) {
        graph.setTransformationUID(id, uid);
    }

    private int addNode(
            String name, OperatorType type,
            OperatorFactory<? extends StreamOperator> factory, String uid) {
        checkMutable();
        int id = nextId;
        StreamNode node = new StreamNode(id, name, uid, type, factory);
        if (type == OperatorType.SOURCE) {
            graph.addSource(node);
        } else if (type == OperatorType.SINK) {
            graph.addSink(node);
        } else {
            graph.addOperator(node);
        }
        nextId++;
        return id;
    }

    private void validateTail(int inputId) {
        checkMutable();
        StreamNode input = graph.getStreamNode(inputId);
        if (input == null) {
            throw new IllegalArgumentException("Unknown upstream node: " + inputId);
        }
        if (input.getType() == OperatorType.SINK || !graph.getSinkIDs().isEmpty()) {
            throw new IllegalStateException("Cannot attach nodes after the sink");
        }
        if (!input.getOutEdges().isEmpty()) {
            throw new IllegalArgumentException("Branching is not supported in the first runtime");
        }
    }

    private void checkMutable() {
        if (graph.isSealed()) {
            throw new IllegalStateException("The pipeline graph is already finalized");
        }
    }

    public StreamGraph getStreamGraph() {
        if (!graph.isSealed()) {
            LocalExecutionPlan validatedPlan = LocalExecutionPlan.compile(graph);
            graph.seal();
            executionPlan = validatedPlan;
        }
        return graph;
    }

    public void execute(String jobName) throws Exception {
        Objects.requireNonNull(jobName, "jobName");
        StreamTask task;
        synchronized (this) {
            if (submitted) {
                throw new IllegalStateException("Pipeline was already submitted");
            }
            getStreamGraph();
            task = new StreamTask(executionPlan);
            submitted = true;
        }
        task.execute();
    }

    public StreamExecutionHandle executeAsync(String jobName) {
        Objects.requireNonNull(jobName, "jobName");
        final StreamTask task;
        synchronized (this) {
            if (submitted) {
                throw new IllegalStateException("Pipeline was already submitted");
            }
            getStreamGraph();
            task = new StreamTask(executionPlan);
            submitted = true;
        }
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
