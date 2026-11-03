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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.common.configuration.ConfigOption;
import io.yak.ops.cdc.common.configuration.ConfigOptions;
import io.yak.ops.cdc.common.configuration.Configuration;
import io.yak.ops.cdc.common.data.GenericRecordData;
import io.yak.ops.cdc.common.data.binary.BinaryStringData;
import io.yak.ops.cdc.common.event.AddColumnEvent;
import io.yak.ops.cdc.common.event.CreateTableEvent;
import io.yak.ops.cdc.common.event.DataChangeEvent;
import io.yak.ops.cdc.common.event.Event;
import io.yak.ops.cdc.common.event.OperationType;
import io.yak.ops.cdc.common.event.SchemaChangeEvent;
import io.yak.ops.cdc.common.exceptions.SchemaEvolveException;
import io.yak.ops.cdc.common.event.TableId;
import io.yak.ops.cdc.common.schema.Column;
import io.yak.ops.cdc.common.schema.Schema;
import io.yak.ops.cdc.common.types.DataTypes;
import io.yak.ops.cdc.common.factories.DataSinkFactory;
import io.yak.ops.cdc.common.factories.DataSourceFactory;
import io.yak.ops.cdc.common.factories.FactoryHelper;
import io.yak.ops.cdc.common.sink.DataSink;
import io.yak.ops.cdc.common.sink.EventSinkProvider;
import io.yak.ops.cdc.common.sink.MetadataApplier;
import io.yak.ops.cdc.common.source.DataSource;
import io.yak.ops.cdc.common.source.EventSourceProvider;
import io.yak.ops.cdc.common.source.MetadataAccessor;
import io.yak.ops.cdc.composer.PipelineExecution;
import io.yak.ops.cdc.composer.definition.PipelineDef;
import io.yak.ops.cdc.composer.definition.RouteDef;
import io.yak.ops.cdc.composer.definition.SinkDef;
import io.yak.ops.cdc.composer.definition.SourceDef;
import io.yak.ops.cdc.composer.definition.TransformDef;
import io.yak.ops.cdc.runtime.connectors.RuntimeSinkProvider;
import io.yak.ops.cdc.runtime.connectors.RuntimeSourceProvider;
import io.yak.ops.cdc.runtime.datastream.StreamExecutionEnvironment;
import io.yak.ops.cdc.runtime.graph.OperatorType;
import io.yak.ops.cdc.runtime.graph.StreamGraph;
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
    private static final List<SchemaChangeEvent> APPLIED = new CopyOnWriteArrayList<>();
    private static final List<Event> DELIVERY_TRACE = new CopyOnWriteArrayList<>();
    private static final List<Event> CDC_EVENTS = createCdcEvents();

    private static List<Event> createCdcEvents() {
        TableId tableId = TableId.tableId("db", "orders");
        Schema schema = Schema.newBuilder()
                .physicalColumn("id", DataTypes.BIGINT())
                .physicalColumn("name", DataTypes.STRING())
                .primaryKey("id").build();
        CreateTableEvent create = new CreateTableEvent(tableId, schema);
        DataChangeEvent insert = DataChangeEvent.insertEvent(tableId,
                GenericRecordData.of(1L, BinaryStringData.fromString("first")));
        DataChangeEvent update = DataChangeEvent.updateEvent(tableId,
                GenericRecordData.of(1L, BinaryStringData.fromString("first")),
                GenericRecordData.of(1L, BinaryStringData.fromString("second")));
        AddColumnEvent add = new AddColumnEvent(tableId, List.of(
                AddColumnEvent.last(Column.physicalColumn("remark", DataTypes.STRING()))));
        DataChangeEvent following = DataChangeEvent.insertEvent(tableId,
                GenericRecordData.of(2L, BinaryStringData.fromString("new"),
                        BinaryStringData.fromString("remark")));
        return List.of(create, insert, update, add, following);
    }

    private static final class TestEvent implements Event {
        private final String value;

        private TestEvent(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TestEvent)) {
                return false;
            }
            TestEvent that = (TestEvent) other;
            return java.util.Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hashCode(value);
        }
    }

    @BeforeEach
    void clear() {
        RECEIVED.clear();
        APPLIED.clear();
        DELIVERY_TRACE.clear();
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
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        StandalonePipelineExecution execution = assertInstanceOf(StandalonePipelineExecution.class,
                new StandalonePipelineComposer(env).compose(
                        definition(Map.of("tables", "db.orders"),
                                Map.of("target", "target.orders"),
                                Map.of("name", "orders", "operator.uid.prefix", "replica"),
                                List.of(), List.of())));
        StreamGraph graph = env.getStreamGraph();
        assertEquals(List.of(OperatorType.SOURCE, OperatorType.TRANSFORM,
                        OperatorType.TRANSFORM, OperatorType.TRANSFORM, OperatorType.SINK),
                graph.getOrderedNodes().stream().map(n -> n.getType()).toList());
        assertEquals(List.of("Yak Ops CDC Event Source: test-source", "SchemaOperator",
                        "PrePartition", "PostPartition", "Yak Ops CDC Event Sink: test-sink"),
                graph.getOrderedNodes().stream().map(n -> n.getOperatorName()).toList());
        assertEquals("replica-source", graph.getOrderedNodes().get(0).getTransformationUID());
        assertEquals("replica-schema-operator", graph.getOrderedNodes().get(1).getTransformationUID());
        assertEquals("replica-pre-partition", graph.getOrderedNodes().get(2).getTransformationUID());
        assertEquals("replica-post-partition", graph.getOrderedNodes().get(3).getTransformationUID());
        assertEquals("replica-sink-writer", graph.getOrderedNodes().get(4).getTransformationUID());
        assertEquals(4, graph.getEdges().size());
        assertEquals(0, RECEIVED.size(), "Composer must not execute any Event");

        PipelineExecution.ExecutionInfo info = execution.execute();
        assertEquals("orders", info.getDescription());
        assertEquals(info.getId(), execution.getHandle().getId());
        execution.getHandle().getCompletion().get(5, TimeUnit.SECONDS);
        assertEquals(List.of(new TestEvent("from-source")), RECEIVED);
        assertThrows(IllegalStateException.class, execution::execute);
    }

    @Test
    void composerTransportsSchemaAndDataChangeEventsInSourceOrder() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        StandalonePipelineExecution execution = assertInstanceOf(
                StandalonePipelineExecution.class,
                new StandalonePipelineComposer(env).compose(
                        definition(
                                Map.of("tables", "db.orders", "emit-cdc-events", "true"),
                                Map.of("target", "target.orders"),
                                Map.of("operator.uid.prefix", "cdc"),
                                List.of(), List.of())));
        StreamGraph graph = env.getStreamGraph();
        assertEquals(5, graph.getNodes().size());
        assertEquals(4, graph.getEdges().size());
        assertEquals("cdc-source", graph.getStreamNode(1).getTransformationUID());
        assertEquals("cdc-schema-operator", graph.getStreamNode(2).getTransformationUID());
        assertEquals("cdc-sink-writer", graph.getStreamNode(5).getTransformationUID());
        assertTrue(RECEIVED.isEmpty());

        execution.execute();
        execution.getHandle().getCompletion().get(5, TimeUnit.SECONDS);
        assertEquals(CDC_EVENTS, RECEIVED);
        assertEquals(List.of(CDC_EVENTS.get(0), CDC_EVENTS.get(3)), APPLIED);
        assertEquals(List.of(CDC_EVENTS.get(0), CDC_EVENTS.get(0), CDC_EVENTS.get(1),
                CDC_EVENTS.get(2), CDC_EVENTS.get(3), CDC_EVENTS.get(3), CDC_EVENTS.get(4)),
                DELIVERY_TRACE, "Schema must be applied before downstream consumes each DDL");
        for (int i = 0; i < CDC_EVENTS.size(); i++) {
            assertSame(CDC_EVENTS.get(i), RECEIVED.get(i));
        }
        assertInstanceOf(CreateTableEvent.class, RECEIVED.get(0));
        assertEquals(OperationType.INSERT, ((DataChangeEvent) RECEIVED.get(1)).op());
        assertEquals(OperationType.UPDATE, ((DataChangeEvent) RECEIVED.get(2)).op());
        assertInstanceOf(AddColumnEvent.class, RECEIVED.get(3));
        assertEquals(3, ((DataChangeEvent) RECEIVED.get(4)).after().getArity());
    }

    @Test
    void configuredTransformDoesNotSilentlyPassThroughConcreteSchemaEvents() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        List<TransformDef> rules = List.of(
                new TransformDef("db.orders", "id", null, null, null, null, null, null));
        StandalonePipelineExecution execution = assertInstanceOf(
                StandalonePipelineExecution.class,
                new StandalonePipelineComposer(env).compose(
                        definition(
                                Map.of("tables", "db.orders", "emit-cdc-events", "true"),
                                Map.of("target", "target.orders"), Map.of(),
                                List.of(), rules)));
        assertEquals(7, env.getStreamGraph().getNodes().size());
        execution.execute();
        CompletionException error = assertThrows(CompletionException.class,
                () -> execution.getHandle().getCompletion().join());
        assertInstanceOf(UnsupportedOperationException.class, error.getCause());
        assertTrue(error.getCause().getMessage().contains("PreTransform"));
        assertTrue(RECEIVED.isEmpty());
    }

    @Test
    void transformDefinitionsProduceFlinkCdcPreAndPostStagesWithoutRunningExpressions() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        List<TransformDef> transforms = List.of(
                new TransformDef("db.orders", "id", null, null, null, null, null, null),
                new TransformDef("db.users", "id", null, null, null, null, null, null));
        StandalonePipelineExecution execution = assertInstanceOf(StandalonePipelineExecution.class,
                new StandalonePipelineComposer(env).compose(
                        definition(Map.of("tables", "db.orders"),
                                Map.of("target", "target.orders"),
                                Map.of("operator.uid.prefix", "job"),
                                List.of(), transforms)));
        assertEquals(List.of("Yak Ops CDC Event Source: test-source",
                        "Transform:Schema", "Transform:Data",
                        "SchemaOperator", "PrePartition", "PostPartition",
                        "Yak Ops CDC Event Sink: test-sink"),
                env.getStreamGraph().getOrderedNodes().stream().map(n -> n.getOperatorName()).toList());
        assertEquals(6, env.getStreamGraph().getEdges().size());
        assertEquals("job-post-transform", env.getStreamGraph().getOrderedNodes().get(2).getTransformationUID());
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
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        new StandalonePipelineComposer(env, TestSourceFactory.class.getClassLoader())
                .compose(definition());
        assertEquals(5, env.getStreamGraph().getNodes().size());
        assertThrows(NullPointerException.class,
                () -> new StandalonePipelineComposer(null));
    }

    @Test
    void schemaUidMatchesFlinkCdcPrecedenceAndRejectsConflict() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        new StandalonePipelineComposer(env).compose(definition(
                Map.of("tables", "db.orders"), Map.of("target", "target.orders"),
                Map.of("schema.operator.uid", "explicit-schema"), List.of(), List.of()));
        assertEquals("explicit-schema",
                env.getStreamGraph().getOrderedNodes().get(1).getTransformationUID());
        assertThrows(IllegalArgumentException.class,
                () -> new StandalonePipelineComposer(StreamExecutionEnvironment.getLocalEnvironment())
                        .compose(definition(Map.of("tables", "db.orders"),
                                Map.of("target", "target.orders"),
                                Map.of("operator.uid.prefix", "job", "schema.operator.uid", "custom"),
                                List.of(), List.of())));
    }

    @Test
    void batchModeUsesBatchStageNames() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        new StandalonePipelineComposer(env).compose(definition(
                Map.of("tables", "db.orders"), Map.of("target", "target.orders"),
                Map.of("execution.runtime-mode", "BATCH"), List.of(), List.of()));
        assertEquals(List.of("Yak Ops CDC Event Source: test-source",
                        "SchemaBatchOperator", "BatchPrePartition", "BatchPostPartition",
                        "Yak Ops CDC Event Sink: test-sink"),
                env.getStreamGraph().getOrderedNodes().stream().map(n -> n.getOperatorName()).toList());
    }

    @Test
    void schemaApplicationFailureBlocksFollowingDataEvents() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        StandalonePipelineExecution execution = assertInstanceOf(
                StandalonePipelineExecution.class,
                new StandalonePipelineComposer(env).compose(definition(
                        Map.of("tables", "db.orders", "emit-cdc-events", "true"),
                        Map.of("target", "target.orders", "fail-schema", "true"),
                        Map.of(), List.of(), List.of())));
        execution.execute();
        CompletionException failure = assertThrows(CompletionException.class,
                () -> execution.getHandle().getCompletion().join());
        assertInstanceOf(SchemaEvolveException.class, failure.getCause());
        assertTrue(RECEIVED.isEmpty(), "No DML may reach the sink after failed CREATE TABLE");
    }

    @Test
    void asyncTransformConfigurationIsRejectedInsteadOfIgnored() {
        assertThrows(UnsupportedOperationException.class,
                () -> new StandalonePipelineComposer(StreamExecutionEnvironment.getLocalEnvironment())
                        .compose(definition(Map.of("tables", "db.orders"),
                                Map.of("target", "target.orders"),
                                Map.of("transform.async-execution.enabled", "true"),
                                List.of(), List.of())));
    }

    public static class TestSourceFactory implements DataSourceFactory {
        private static final ConfigOption<String> TABLES =
                ConfigOptions.key("tables").stringType().noDefaultValue();
        private static final ConfigOption<Boolean> PARALLEL_METADATA =
                ConfigOptions.key("parallel-metadata").booleanType().defaultValue(false);
        private static final ConfigOption<Boolean> LEGACY =
                ConfigOptions.key("legacy-provider").booleanType().defaultValue(false);
        private static final ConfigOption<Boolean> EMIT_CDC =
                ConfigOptions.key("emit-cdc-events").booleanType().defaultValue(false);

        @Override public String identifier() { return "test-source"; }
        @Override public Set<ConfigOption<?>> requiredOptions() { return Set.of(TABLES); }
        @Override public Set<ConfigOption<?>> optionalOptions() {
            return Set.of(PARALLEL_METADATA, LEGACY, EMIT_CDC);
        }

        @Override
        public DataSource createDataSource(Context context) {
            FactoryHelper.createFactoryHelper(this, context).validate();
            boolean parallel = context.getFactoryConfiguration().get(PARALLEL_METADATA);
            boolean legacy = context.getFactoryConfiguration().get(LEGACY);
            boolean emitCdc = context.getFactoryConfiguration().get(EMIT_CDC);
            EventSourceProvider provider = legacy
                    ? new EventSourceProvider() {}
                    : (RuntimeSourceProvider) () -> output -> {
                        if (emitCdc) {
                            for (Event event : CDC_EVENTS) {
                                output.collect(event);
                            }
                        } else {
                            output.collect(new TestEvent("from-source"));
                        }
                    };
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
        private static final ConfigOption<Boolean> FAIL_SCHEMA =
                ConfigOptions.key("fail-schema").booleanType().defaultValue(false);

        @Override public String identifier() { return "test-sink"; }
        @Override public Set<ConfigOption<?>> requiredOptions() { return Set.of(TARGET); }
        @Override public Set<ConfigOption<?>> optionalOptions() { return Set.of(LEGACY, FAIL_SCHEMA); }

        @Override
        public DataSink createDataSink(Context context) {
            FactoryHelper.createFactoryHelper(this, context).validate();
            boolean legacy = context.getFactoryConfiguration().get(LEGACY);
            boolean failSchema = context.getFactoryConfiguration().get(FAIL_SCHEMA);
            EventSinkProvider provider = legacy
                    ? new EventSinkProvider() {}
                    : (RuntimeSinkProvider) () -> event -> {
                        RECEIVED.add(event);
                        DELIVERY_TRACE.add(event);
                    };
            MetadataApplier metadataApplier = event -> {
                if (failSchema) {
                    throw new SchemaEvolveException(event, "test schema failure");
                }
                APPLIED.add(event);
                DELIVERY_TRACE.add(event);
            };
            return new DataSink() {
                @Override public EventSinkProvider getEventSinkProvider() { return provider; }
                @Override public MetadataApplier getMetadataApplier() { return metadataApplier; }
            };
        }
    }
}
