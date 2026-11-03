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

import io.yak.ops.cdc.runtime.graph.StreamGraph;
import io.yak.ops.cdc.runtime.graph.StreamGraphGenerator;
import io.yak.ops.cdc.runtime.operators.OneInputStreamOperator;
import io.yak.ops.cdc.runtime.operators.OperatorFactory;
import io.yak.ops.cdc.runtime.operators.SinkOperator;
import io.yak.ops.cdc.runtime.operators.SourceOperator;
import io.yak.ops.cdc.runtime.tasks.LocalExecutionPlan;
import io.yak.ops.cdc.runtime.tasks.StreamTask;
import io.yak.ops.cdc.runtime.transformations.OneInputTransformation;
import io.yak.ops.cdc.runtime.transformations.SinkTransformation;
import io.yak.ops.cdc.runtime.transformations.SourceTransformation;
import io.yak.ops.cdc.runtime.transformations.Transformation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Local DataStream environment. Records transformations first, then generates and seals
 * the StreamGraph when it is requested or submitted for execution.
 *
 * <p>The standalone builder currently permits exactly one linear source-to-sink pipeline.
 * The general graph model and generator do not inherit that runtime restriction.
 */
public final class StreamExecutionEnvironment {
    private final List<Transformation<?>> transformations = new ArrayList<>();
    private int nextId = 1;
    private Transformation<?> tail;
    private StreamGraph graph;
    private LocalExecutionPlan executionPlan;
    private boolean submitted;

    public static StreamExecutionEnvironment getLocalEnvironment() {
        return new StreamExecutionEnvironment();
    }

    public <T> DataStream<T> fromSource(
            String name, OperatorFactory<? extends SourceOperator<T>> factory) {
        checkMutable();
        if (!transformations.isEmpty()) {
            throw new IllegalStateException("Only one source per pipeline is supported");
        }
        SourceTransformation<T> source = new SourceTransformation<>(nextId, name, factory);
        addTransformation(source);
        return new DataStream<>(this, source);
    }

    <IN, OUT> DataStream<OUT> addTransform(
            Transformation<IN> input,
            String name,
            OperatorFactory<? extends OneInputStreamOperator<IN, OUT>> factory) {
        validateTail(input);
        OneInputTransformation<IN, OUT> transform =
                new OneInputTransformation<>(nextId, name, input, factory);
        addTransformation(transform);
        return new DataStream<>(this, transform);
    }

    <T> void addSink(
            Transformation<T> input,
            String name,
            OperatorFactory<? extends SinkOperator<T>> factory,
            String uid) {
        validateTail(input);
        SinkTransformation<T> sink = new SinkTransformation<>(nextId, name, input, factory);
        validateUid(sink, uid);
        sink.setUid(uid);
        addTransformation(sink);
    }

    void setUid(Transformation<?> transformation, String uid) {
        checkMutable();
        Objects.requireNonNull(transformation, "transformation");
        if (!transformations.contains(transformation)) {
            throw new IllegalArgumentException("Unknown transformation: " + transformation.getId());
        }
        validateUid(transformation, uid);
        transformation.setUid(uid);
    }

    private void addTransformation(Transformation<?> transformation) {
        checkMutable();
        transformations.add(transformation);
        tail = transformation;
        nextId++;
    }

    private void validateUid(Transformation<?> target, String uid) {
        if (uid != null && uid.isBlank()) {
            throw new IllegalArgumentException("UID must not be blank");
        }
        if (uid == null) {
            return;
        }
        for (Transformation<?> existing : transformations) {
            if (existing != target && uid.equals(existing.getUid())) {
                throw new IllegalArgumentException("Duplicate operator UID: " + uid);
            }
        }
    }

    private void validateTail(Transformation<?> input) {
        checkMutable();
        Objects.requireNonNull(input, "input");
        if (tail instanceof SinkTransformation<?>) {
            throw new IllegalStateException("Cannot attach nodes after the sink");
        }
        if (tail != input) {
            throw new IllegalArgumentException(
                    "Branching and multiple inputs are not supported in the first runtime");
        }
    }

    private void checkMutable() {
        if (graph != null) {
            throw new IllegalStateException("The pipeline graph is already finalized");
        }
    }

    public synchronized StreamGraph getStreamGraph() {
        if (graph == null) {
            StreamGraph candidate = new StreamGraphGenerator(transformations).generate();
            LocalExecutionPlan candidatePlan = LocalExecutionPlan.compile(candidate);
            graph = candidate;
            executionPlan = candidatePlan;
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
