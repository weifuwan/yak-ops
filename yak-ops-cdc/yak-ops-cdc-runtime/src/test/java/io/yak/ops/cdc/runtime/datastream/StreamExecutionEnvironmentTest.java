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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.common.event.Event;
import io.yak.ops.cdc.runtime.graph.OperatorType;
import io.yak.ops.cdc.runtime.graph.StreamEdge;
import io.yak.ops.cdc.runtime.graph.StreamGraph;
import io.yak.ops.cdc.runtime.operators.OneInputStreamOperator;
import io.yak.ops.cdc.runtime.operators.Output;
import io.yak.ops.cdc.runtime.operators.SinkOperator;
import io.yak.ops.cdc.runtime.operators.SourceOperator;
import io.yak.ops.cdc.runtime.tasks.StreamTask;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StreamExecutionEnvironmentTest {

    @Test
    void constructsGraphWithoutCreatingOrRunningOperators() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        AtomicInteger created = new AtomicInteger();
        List<Integer> collected = new ArrayList<>();
        DataStream<Integer> source = env.<Integer>fromSource("source", () -> {
            created.incrementAndGet();
            return output -> {
                output.collect(1);
                output.collect(2);
                output.collect(3);
            };
        }).uid("source-uid");

        DataStream<Integer> mapped = source.<Integer>transform("multiply", () -> {
            created.incrementAndGet();
            return (value, output) -> output.collect(value * 10);
        }).uid("multiply-uid");
        DataStream<Integer> filtered = mapped.<Integer>transform("filter",
                () -> (value, output) -> {
                    if (value > 10) {
                        output.collect(value);
                    }
                });
        filtered.addSink("sink", () -> {
            created.incrementAndGet();
            return collected::add;
        }, "sink-uid");

        StreamGraph graph = env.getStreamGraph();
        assertEquals(0, created.get(), "Graph construction must not instantiate operators");
        assertEquals(List.of("source", "multiply", "filter", "sink"),
                graph.getOrderedNodes().stream().map(n -> n.getOperatorName()).toList());
        assertEquals(List.of(OperatorType.SOURCE, OperatorType.TRANSFORM,
                        OperatorType.TRANSFORM, OperatorType.SINK),
                graph.getOrderedNodes().stream().map(n -> n.getType()).toList());
        assertEquals(List.of(1, 2, 3),
                graph.getEdges().stream().map(StreamEdge::getSourceId).toList());
        assertEquals(List.of(2, 3, 4),
                graph.getEdges().stream().map(StreamEdge::getTargetId).toList());
        assertEquals("multiply-uid", graph.getOrderedNodes().get(1).getTransformationUID());
        assertThrows(IllegalStateException.class,
                () -> filtered.transform("too late", () -> (event, output) -> {}));

        env.execute("test");
        assertEquals(List.of(20, 30), collected);
        assertEquals(3, created.get());
        assertThrows(IllegalStateException.class, () -> env.execute("again"));
    }

    @Test
    void supportsDirectSourceToSinkAndPassesCdcEventInstancesUnchanged() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        Event schema = new Event() {};
        Event data = new Event() {};
        List<Event> seen = new ArrayList<>();
        env.<Event>fromSource("source", () -> out -> {
                    out.collect(schema);
                    out.collect(data);
                })
                .addSink("sink", () -> seen::add);
        env.execute("direct");
        assertEquals(List.of(schema, data), seen);
        assertTrue(seen.get(0) == schema);
    }

    @Test
    void materializesOnlyOnDemandAndRetainsTransformationIdsAndGraphIdentity() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        AtomicInteger created = new AtomicInteger();

        DataStream<Integer> source = env.fromSource("input", () -> {
            created.incrementAndGet();
            return output -> output.collect(3);
        });
        DataStream<Integer> transform = source.transform("increment", () -> {
            created.incrementAndGet();
            return (number, output) -> output.collect(number + 1);
        });
        transform.addSink("output", () -> {
            created.incrementAndGet();
            return value -> {};
        }, "sink-uid");

        assertEquals(1, source.getId());
        assertEquals(2, transform.getId());
        assertEquals(0, created.get());
        StreamGraph first = env.getStreamGraph();
        assertTrue(first == env.getStreamGraph());
        assertEquals(0, created.get());
        assertEquals(3, first.getNodes().size());
        assertEquals("sink-uid", first.getStreamNode(3).getTransformationUID());
        assertThrows(IllegalStateException.class, () -> source.uid("too-late"));

        env.execute("lazy");
        assertEquals(3, created.get());
    }

    @Test
    void invalidDefinitionCanBeCompletedAfterFailedMaterialization() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        DataStream<Integer> source = env.fromSource("input", () -> output -> {});
        assertThrows(IllegalArgumentException.class, env::getStreamGraph);
        source.addSink("output", () -> value -> {});
        assertEquals(2, env.getStreamGraph().getNodes().size());
        assertThrows(IllegalStateException.class,
                () -> source.transform("too-late", () -> (value, output) -> {}));
    }

    @Test
    void rejectsMalformedGraphsAndMultipleSourcesOrBranches() {
        StreamExecutionEnvironment noSink = StreamExecutionEnvironment.getLocalEnvironment();
        DataStream<Object> src = noSink.fromSource("source", () -> out -> {});
        assertThrows(IllegalArgumentException.class, noSink::getStreamGraph);
        assertThrows(IllegalStateException.class,
                () -> noSink.fromSource("other", () -> out -> {}));
        DataStream<Object> next = src.transform("first", () -> (input, output) -> {});
        assertThrows(IllegalArgumentException.class,
                () -> src.transform("branch", () -> (input, output) -> {}));
        next.addSink("sink", () -> element -> {});
        assertThrows(IllegalStateException.class,
                () -> next.transform("after sink", () -> (input, output) -> {}));
        assertThrows(IllegalStateException.class,
                () -> next.addSink("second sink", () -> element -> {}));

        StreamExecutionEnvironment duplicateUid = StreamExecutionEnvironment.getLocalEnvironment();
        DataStream<Object> s = duplicateUid.fromSource("src", () -> out -> {}).uid("same");
        assertThrows(IllegalArgumentException.class,
                () -> s.transform("transform", () -> (input, out) -> {}).uid("same"));
    }

    @Test
    void closesOpenedOperatorsOnTransformFailure() throws Exception {
        List<String> lifecycle = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        env.<Integer>fromSource("source", () -> new SourceOperator<>() {
                    @Override public void open() { lifecycle.add("open-source"); }
                    @Override public void run(Output<Integer> output) throws Exception {
                        output.collect(7);
                    }
                    @Override public void close() { lifecycle.add("close-source"); }
                })
                .transform("failure", () -> new OneInputStreamOperator<Integer, Integer>() {
                    @Override public void open() { lifecycle.add("open-transform"); }
                    @Override public void processElement(Integer value, Output<Integer> output) {
                        throw new IllegalStateException("transform failure");
                    }
                    @Override public void close() { lifecycle.add("close-transform"); }
                })
                .addSink("sink", () -> new SinkOperator<Integer>() {
                    @Override public void open() { lifecycle.add("open-sink"); }
                    @Override public void write(Integer value) {}
                    @Override public void close() { lifecycle.add("close-sink"); }
                });
        assertEquals("transform failure",
                assertThrows(IllegalStateException.class, () -> env.execute("fail")).getMessage());
        assertEquals(List.of("open-sink", "open-transform", "open-source",
                             "close-source", "close-transform", "close-sink"), lifecycle);
    }

    @Test
    void closesAlreadyConstructedOperatorsOnSourceAndSinkErrors() {
        for (boolean sourceError : new boolean[]{true, false}) {
            AtomicInteger closed = new AtomicInteger();
            StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
            env.<Integer>fromSource("source", () -> new SourceOperator<>() {
                        @Override public void run(Output<Integer> output) throws Exception {
                            if (sourceError) {
                                throw new IllegalStateException("source");
                            }
                            output.collect(1);
                        }
                        @Override public void close() { closed.incrementAndGet(); }
                    })
                    .addSink("sink", () -> new SinkOperator<Integer>() {
                        @Override public void write(Integer value) {
                            throw new IllegalStateException("sink");
                        }
                        @Override public void close() { closed.incrementAndGet(); }
                    });
            assertEquals(sourceError ? "source" : "sink",
                    assertThrows(IllegalStateException.class, () -> env.execute("error"))
                            .getMessage());
            assertEquals(2, closed.get());
        }
    }

    @Test
    void asyncExecutionCompletesAndSupportsCancellation() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        AtomicInteger seen = new AtomicInteger();
        env.<Integer>fromSource("source", () -> out -> out.collect(4))
                .addSink("sink", () -> element -> seen.set(element));
        StreamExecutionHandle handle = env.executeAsync("async");
        assertNotNull(handle.getId());
        assertEquals("async", handle.getJobName());
        handle.getCompletion().get(5, TimeUnit.SECONDS);
        assertEquals(4, seen.get());

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        StreamExecutionEnvironment streaming = StreamExecutionEnvironment.getLocalEnvironment();
        streaming.<Integer>fromSource("live", () -> new SourceOperator<>() {
                    @Override public void run(Output<Integer> output) throws Exception {
                        started.countDown();
                        cancelled.await();
                    }
                    @Override public void cancel() { cancelled.countDown(); }
                })
                .addSink("terminal", () -> element -> {});
        StreamExecutionHandle running = streaming.executeAsync("live");
        assertTrue(started.await(5, TimeUnit.SECONDS));
        running.cancel();
        assertTrue(running.getCompletion().isDone());
    }

    @Test
    void asyncErrorsStayObservable() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        env.<Integer>fromSource("source", () -> out -> {
                    throw new IllegalStateException("broken");
                })
                .addSink("sink", () -> element -> {});
        StreamExecutionHandle handle = env.executeAsync("failure");
        CompletionException error =
                assertThrows(CompletionException.class, () -> handle.getCompletion().join());
        assertEquals("broken", error.getCause().getMessage());
    }
}
