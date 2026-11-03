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

package io.yak.ops.cdc.runtime.tasks;

import io.yak.ops.cdc.runtime.graph.StreamGraph;
import io.yak.ops.cdc.runtime.graph.StreamNode;
import io.yak.ops.cdc.runtime.operators.OneInputStreamOperator;
import io.yak.ops.cdc.runtime.operators.Output;
import io.yak.ops.cdc.runtime.operators.SinkOperator;
import io.yak.ops.cdc.runtime.operators.SourceOperator;
import io.yak.ops.cdc.runtime.operators.StreamOperator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Execute a linear graph on the calling thread with synchronous in-memory operator chaining.
 *
 * <p>Operators are created once per task; downstream operators are opened before the source.
 * Event delivery is synchronous through Output.collect. There are no threads or queues per node.
 */
public final class StreamTask {
    private final StreamGraph graph;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile SourceOperator<?> runningSource;
    private volatile Thread runningThread;

    public StreamTask(StreamGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
    }

    @SuppressWarnings("unchecked")
    public void execute() throws Exception {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("StreamTask cannot be run more than once");
        }

        List<StreamOperator> operators = new ArrayList<>();
        Throwable failure = null;
        try {
            for (StreamNode node : graph.getOrderedNodes()) {
                StreamOperator op = Objects.requireNonNull(
                        node.operatorFactory().create(),
                        "Operator factory returned null for " + node.name());
                operators.add(op);
            }

            if (!(operators.get(0) instanceof SourceOperator<?> source)) {
                throw new IllegalStateException("Source node did not create SourceOperator");
            }
            if (!(operators.get(operators.size() - 1) instanceof SinkOperator<?>)) {
                throw new IllegalStateException("Sink node did not create SinkOperator");
            }
            for (int i = 1; i < operators.size() - 1; i++) {
                if (!(operators.get(i) instanceof OneInputStreamOperator<?, ?>)) {
                    throw new IllegalStateException(
                            "Transform node did not create OneInputStreamOperator: " + i);
                }
            }

            runningSource = source;
            runningThread = Thread.currentThread();
            for (int i = operators.size() - 1; i >= 0; i--) {
                operators.get(i).open();
            }
            if (cancelled.get()) {
                return;
            }

            SinkOperator<Object> sink = (SinkOperator<Object>) operators.get(operators.size() - 1);
            Output<Object> output = sink::write;
            for (int i = operators.size() - 2; i >= 1; i--) {
                OneInputStreamOperator<Object, Object> operator =
                        (OneInputStreamOperator<Object, Object>) operators.get(i);
                Output<Object> next = output;
                output = event -> operator.processElement(event, next);
            }

            ((SourceOperator<Object>) source).run(output);
        } catch (Exception | Error error) {
            failure = error;
            throw error;
        } finally {
            runningSource = null;
            runningThread = null;
            Exception closeFailure = null;
            for (StreamOperator operator : operators) {
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

    /** Cooperative cancellation; source implementations should honor cancel() or interruption. */
    public void cancel() {
        cancelled.set(true);
        SourceOperator<?> source = runningSource;
        if (source != null) {
            try {
                source.cancel();
            } catch (Exception error) {
                // Interrupt the task anyway. Any running failure propagates through execute().
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
