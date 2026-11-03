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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes a compiled local execution plan in memory, using synchronous per-edge output links.
 *
 * <p>Operator instances are created once per task and opened downstream-first. Only the source
 * drives record delivery. No thread, queue, network or distributed scheduler is created per node.
 */
public final class StreamTask {
    private final LocalExecutionPlan plan;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile SourceOperator<?> runningSource;
    private volatile Thread runningThread;

    public StreamTask(StreamGraph graph) {
        this(LocalExecutionPlan.compile(graph));
    }

    public StreamTask(LocalExecutionPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    @SuppressWarnings("unchecked")
    public void execute() throws Exception {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("StreamTask cannot be run more than once");
        }
        if (cancelled.get()) {
            return;
        }

        Map<Integer, StreamOperator> operatorsById = new LinkedHashMap<>();
        List<StreamOperator> created = new ArrayList<>();
        Throwable failure = null;
        try {
            for (StreamNode node : plan.getNodes()) {
                StreamOperator operator = Objects.requireNonNull(
                        node.getOperatorFactory().create(),
                        "Operator factory returned null for " + node.getOperatorName());
                // Register immediately so even a wrongly typed instance is closed on failure.
                created.add(operator);
                operatorsById.put(node.getId(), operator);
                validateOperator(node, operator);
            }

            SourceOperator<Object> source =
                    (SourceOperator<Object>) operatorsById.get(plan.getSourceId());
            runningSource = source;
            runningThread = Thread.currentThread();

            for (int i = created.size() - 1; i >= 0; i--) {
                created.get(i).open();
            }
            if (cancelled.get()) {
                return;
            }

            // Every output is keyed by the actual upstream node and follows a StreamEdge.
            // Reverse traversal makes the downstream output available before its predecessor.
            Map<Integer, Output<Object>> outputsBySource = new LinkedHashMap<>();
            List<StreamEdge> edges = plan.getEdges();
            for (int i = edges.size() - 1; i >= 0; i--) {
                StreamEdge edge = edges.get(i);
                StreamOperator downstreamOperator = operatorsById.get(edge.getTargetId());
                Output<Object> output;
                if (downstreamOperator instanceof SinkOperator) {
                    SinkOperator<Object> sink = (SinkOperator<Object>) downstreamOperator;
                    output = sink::write;
                } else if (downstreamOperator instanceof OneInputStreamOperator) {
                    OneInputStreamOperator<Object, Object> transform =
                            (OneInputStreamOperator<Object, Object>) downstreamOperator;
                    Output<Object> next = outputsBySource.get(edge.getTargetId());
                    if (next == null) {
                        throw new IllegalStateException(
                                "Missing downstream output for edge " + edge);
                    }
                    output = element -> transform.processElement(element, next);
                } else {
                    throw new IllegalStateException(
                            "Unsupported downstream operator for edge " + edge);
                }
                if (outputsBySource.put(edge.getSourceId(), output) != null) {
                    throw new IllegalStateException(
                            "Multiple outputs from source node " + edge.getSourceId());
                }
            }

            Output<Object> sourceOutput = outputsBySource.get(plan.getSourceId());
            if (sourceOutput == null) {
                throw new IllegalStateException("Missing output for local source");
            }
            source.run(sourceOutput);
        } catch (Exception | Error error) {
            failure = error;
            throw error;
        } finally {
            runningSource = null;
            runningThread = null;
            Exception closeFailure = null;
            for (StreamOperator operator : created) {
                try {
                    operator.close();
                } catch (Exception error) {
                    if (failure != null) {
                        failure.addSuppressed(error);
                    } else if (closeFailure == null) {
                        closeFailure = error;
                    } else {
                        closeFailure.addSuppressed(error);
                    }
                }
            }
            if (failure == null && closeFailure != null) {
                throw closeFailure;
            }
        }
    }

    private static void validateOperator(StreamNode node, StreamOperator operator) {
        if (node.getType() == OperatorType.SOURCE && operator instanceof SourceOperator) {
            return;
        }
        if (node.getType() == OperatorType.TRANSFORM
                && operator instanceof OneInputStreamOperator) {
            return;
        }
        if (node.getType() == OperatorType.SINK && operator instanceof SinkOperator) {
            return;
        }
        throw new IllegalStateException(
                "Wrong operator type created for " + node.getOperatorName()
                        + " (" + node.getType() + ")");
    }

    /** Cooperative cancellation; source implementations must honor cancellation or interruption. */
    public void cancel() {
        cancelled.set(true);
        SourceOperator<?> source = runningSource;
        if (source != null) {
            try {
                source.cancel();
            } catch (Exception error) {
                // Still interrupt the task if a source cancellation callback fails.
            }
        }
        Thread thread = runningThread;
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
