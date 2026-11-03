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

package io.yak.ops.cdc.composer.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.common.configuration.ConfigOption;
import io.yak.ops.cdc.common.configuration.ConfigOptions;
import io.yak.ops.cdc.common.configuration.Configuration;
import io.yak.ops.cdc.common.event.Event;
import io.yak.ops.cdc.common.factories.DataSinkFactory;
import io.yak.ops.cdc.common.factories.DataSourceFactory;
import io.yak.ops.cdc.common.factories.FactoryHelper;
import io.yak.ops.cdc.common.sink.DataSink;
import io.yak.ops.cdc.common.sink.EventSinkProvider;
import io.yak.ops.cdc.common.sink.MetadataApplier;
import io.yak.ops.cdc.common.source.DataSource;
import io.yak.ops.cdc.common.source.EventSourceProvider;
import io.yak.ops.cdc.common.source.MetadataAccessor;
import io.yak.ops.cdc.composer.definition.PipelineDef;
import io.yak.ops.cdc.composer.definition.RouteDef;
import io.yak.ops.cdc.composer.definition.SinkDef;
import io.yak.ops.cdc.composer.definition.SourceDef;
import io.yak.ops.cdc.composer.definition.TransformDef;
import io.yak.ops.cdc.runtime.connectors.RuntimeSinkProvider;
import io.yak.ops.cdc.runtime.connectors.RuntimeSourceProvider;
import io.yak.ops.cdc.runtime.datastream.StreamExecutionEnvironment;
import io.yak.ops.cdc.runtime.graph.OperatorType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StandalonePipelineComposerTest {

    private static final List<Event> RECEIVED = new CopyOnWriteArrayList<>();

    private record TestEvent(String value) implements Event {}

    @BeforeEach
    void clear() {
        RECEIVED.clear();
    }

    private PipelineDef definition(
            Map<String, String> sourceOptions,
            Map<String, String> sinkOptions,
            Map<String, String> pipelineOptions,
            List<RouteDef> routes,
            List<TransformDef> transforms) {
        return new PipelineDef(
                new SourceDef("test-source", null, Configuration.fromMap(sourceOptions)),
                new SinkDef("test-sink", null, Configuration.fromMap(sinkOptions)),
                routes, transforms, List.of(), List.of(), Configuration.fromMap(pipelineOptions));
    }

    private PipelineDef definition() {
        return definition(Map.of("tables", "db.orders"),
                Map.of("target", "target.orders"), Map.of(), List.of(), List.of());
    }

    @Test
    void composerBuildsSourceToSinkAndExecutionDelegatesToRuntime() throws Exception {
        var env = StreamExecutionEnvironment.getLocalEnvironment();
        var execution = assertInstanceOf(StandalonePipelineExecution.class,
                new StandalonePipelineComposer(env).compose(
                        definition(Map.of("tables", "db.orders"),
                                Map.of("target", "target.orders"),
                                Map.of("name", "orders", "operator.uid.prefix", "replica"),
                                List.of(), List.of())));
        var graph = env.getStreamGraph();
        assertEquals(List.of(OperatorType.SOURCE, OperatorType.SINK),
                graph.getOrderedNodes().stream().map(n -> n.type()).toList());
        assertEquals("replica-source", graph.getOrderedNodes().get(0).uid());
        assertEquals("replica-sink-writer", graph.getOrderedNodes().get(1).uid());
        assertEquals(1, graph.getEdges().size());
        assertEquals(0, RECEIVED.size(), "Composer must not execute any Event");

        var info = execution.execute();
        assertEquals("orders", info.getDescription());
        assertEquals(info.getId(), execution.getHandle().getId());
        execution.getHandle().getCompletion().get(5, TimeUnit.SECONDS);
        assertEquals(List.of(new TestEvent("from-source")), RECEIVED);
        assertThrows(IllegalStateException.class, execution::execute);
    }

    @Test
    void transformDefinitionsProduceFlinkCdcPreAndPostStagesWithoutRunningExpressions() {
        var env = StreamExecutionEnvironment.getLocalEnvironment();
        var transforms = List.of(
                new TransformDef("db.orders", "id", null, null, null, null, null, null),
                new TransformDef("db.users", "id", null, null, null, null, null, null));
        var execution = assertInstanceOf(StandalonePipelineExecution.class,
                new StandalonePipelineComposer(env).compose(
                        definition(Map.of("tables", "db.orders"),
                                Map.of("target", "target.orders"),
                                Map.of("operator.uid.prefix", "job"),
                                List.of(), transforms)));
        assertEquals(List.of("Yak Ops CDC Event Source: test-source",
                        "Transform:Schema", "Transform:Data",
                        "Yak Ops CDC Event Sink: test-sink"),
                env.getStreamGraph().getOrderedNodes().stream().map(n -> n.name()).toList());
        assertEquals(3, env.getStreamGraph().getEdges().size());
        assertEquals("job-post-transform", env.getStreamGraph().getOrderedNodes().get(2).uid());
        execution.execute();
        CompletionException error = assertThrows(CompletionException.class,
                () -> execution.getHandle().getCompletion().join());
        assertInstanceOf(UnsupportedOperationException.class, error.getCause());
        assertTrue(error.getCause().getMessage().contains("PreTransform"));
        assertTrue(RECEIVED.isEmpty());
    }

    @Test
    void rejectsUnsupportedTopologyInsteadOfSilentlyDroppingSemantics() {
        assertThrows(UnsupportedOperationException.class,
                () -> new StandalonePipelineComposer(StreamExecutionEnvironment.getLocalEnvironment())
                        .compose(definition(Map.of("tables", "db.orders"),
                                Map.of("target", "target.orders"),
                                Map.of("parallelism", "2"), List.of(), List.of())));
        assertThrows(UnsupportedOperationException.class,
                () -> new StandalonePipelineComposer(StreamExecutionEnvironment.getLocalEnvironment())
                        .compose(definition(Map.of("tables", "db.orders"),
                                Map.of("target", "target.orders"), Map.of(),
                                List.of(new RouteDef("db.orders", "dst.orders", null, null)),
                                List.of())));
        assertThrows(UnsupportedOperationException.class,
                () -> new StandalonePipelineComposer(StreamExecutionEnvironment.getLocalEnvironment())
                        .compose(definition(Map.of("tables", "db.orders"),
                                Map.of("target", "target.orders"),
                                Map.of("sink.partitioning.strategy", "TABLE_ID"),
                                List.of(), List.of())));
        assertThrows(UnsupportedOperationException.class,
                () -> new StandalonePipelineComposer(StreamExecutionEnvironment.getLocalEnvironment())
                        .compose(definition(Map.of("tables", "db.orders",
                                        "parallel-metadata", "true"),
                                Map.of("target", "target.orders"), Map.of(), List.of(), List.of())));
    }

    @Test
    void rejectsInvalidFactoryOptionsAndUnsupportedProviderTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> new StandalonePipelineComposer(StreamExecutionEnvironment.getLocalEnvironment())
                        .compose(definition(Map.of("tables", "db.orders", "unknown", "x"),
                                Map.of("target", "target.orders"), Map.of(), List.of(), List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> new StandalonePipelineComposer(StreamExecutionEnvironment.getLocalEnvironment())
                        .compose(definition(Map.of(), Map.of("target", "target.orders"),
                                Map.of(), List.of(), List.of())));
        assertThrows(IllegalStateException.class,
                () -> new StandalonePipelineComposer(StreamExecutionEnvironment.getLocalEnvironment())
                        .compose(definition(Map.of("tables", "db.orders",
                                        "legacy-provider", "true"),
                                Map.of("target", "target.orders"), Map.of(), List.of(), List.of())));
        assertThrows(IllegalStateException.class,
                () -> new StandalonePipelineComposer(StreamExecutionEnvironment.getLocalEnvironment())
                        .compose(definition(Map.of("tables", "db.orders"),
                                Map.of("target", "target.orders", "legacy-provider", "true"),
                                Map.of(), List.of(), List.of())));
    }

    @Test
    void supportsExplicitClassLoaderAndInjection() {
        var env = StreamExecutionEnvironment.getLocalEnvironment();
        new StandalonePipelineComposer(env, TestSourceFactory.class.getClassLoader())
                .compose(definition());
        assertEquals(2, env.getStreamGraph().getNodes().size());
        assertThrows(NullPointerException.class,
                () -> new StandalonePipelineComposer(null));
    }

    public static class TestSourceFactory implements DataSourceFactory {
        private static final ConfigOption<String> TABLES =
                ConfigOptions.key("tables").stringType().noDefaultValue();
        private static final ConfigOption<Boolean> PARALLEL_METADATA =
                ConfigOptions.key("parallel-metadata").booleanType().defaultValue(false);
        private static final ConfigOption<Boolean> LEGACY =
                ConfigOptions.key("legacy-provider").booleanType().defaultValue(false);

        @Override public String identifier() { return "test-source"; }
        @Override public Set<ConfigOption<?>> requiredOptions() { return Set.of(TABLES); }
        @Override public Set<ConfigOption<?>> optionalOptions() {
            return Set.of(PARALLEL_METADATA, LEGACY);
        }

        @Override
        public DataSource createDataSource(Context context) {
            FactoryHelper.createFactoryHelper(this, context).validate();
            boolean parallel = context.getFactoryConfiguration().get(PARALLEL_METADATA);
            boolean legacy = context.getFactoryConfiguration().get(LEGACY);
            EventSourceProvider provider = legacy
                    ? new EventSourceProvider() {}
                    : (RuntimeSourceProvider) () ->
                            output -> output.collect(new TestEvent("from-source"));
            return new DataSource() {
                @Override public EventSourceProvider getEventSourceProvider() { return provider; }
                @Override public MetadataAccessor getMetadataAccessor() { return null; }
                @Override public boolean isParallelMetadataSource() { return parallel; }
            };
        }
    }

    public static class TestSinkFactory implements DataSinkFactory {
        private static final ConfigOption<String> TARGET =
                ConfigOptions.key("target").stringType().noDefaultValue();
        private static final ConfigOption<Boolean> LEGACY =
                ConfigOptions.key("legacy-provider").booleanType().defaultValue(false);

        @Override public String identifier() { return "test-sink"; }
        @Override public Set<ConfigOption<?>> requiredOptions() { return Set.of(TARGET); }
        @Override public Set<ConfigOption<?>> optionalOptions() { return Set.of(LEGACY); }

        @Override
        public DataSink createDataSink(Context context) {
            FactoryHelper.createFactoryHelper(this, context).validate();
            boolean legacy = context.getFactoryConfiguration().get(LEGACY);
            EventSinkProvider provider = legacy
                    ? new EventSinkProvider() {}
                    : (RuntimeSinkProvider) () -> RECEIVED::add;
            MetadataApplier metadataApplier = event -> {};
            return new DataSink() {
                @Override public EventSinkProvider getEventSinkProvider() { return provider; }
                @Override public MetadataApplier getMetadataApplier() { return metadataApplier; }
            };
        }
    }
}
