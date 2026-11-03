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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.common.configuration.ConfigOption;
import io.yak.ops.cdc.common.configuration.ConfigOptions;
import io.yak.ops.cdc.common.configuration.Configuration;
import io.yak.ops.cdc.common.event.DataChangeEvent;
import io.yak.ops.cdc.common.event.TableId;
import io.yak.ops.cdc.common.factories.DataSinkFactory;
import io.yak.ops.cdc.common.factories.DataSourceFactory;
import io.yak.ops.cdc.common.factories.FactoryHelper;
import io.yak.ops.cdc.common.function.HashFunction;
import io.yak.ops.cdc.common.function.HashFunctionProvider;
import io.yak.ops.cdc.common.pipeline.HashFunctionStrategy;
import io.yak.ops.cdc.common.pipeline.RuntimeExecutionMode;
import io.yak.ops.cdc.common.route.RouteRule;
import io.yak.ops.cdc.common.schema.Schema;
import io.yak.ops.cdc.common.sink.DataSink;
import io.yak.ops.cdc.common.sink.DefaultDataChangeEventHashFunctionProvider;
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
import io.yak.ops.cdc.composer.standalone.StandalonePipelinePlan.StageType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StandalonePipelineComposerTest {

    private static final StandalonePipelineComposer COMPOSER = new StandalonePipelineComposer();

    private PipelineDef definition(Map<String, String> sourceOptions,
                                   Map<String, String> sinkOptions,
                                   Map<String, String> pipelineOptions) {
        return new PipelineDef(
                new SourceDef("test-source", null, Configuration.fromMap(sourceOptions)),
                new SinkDef("test-sink", null, Configuration.fromMap(sinkOptions)),
                List.of(new RouteDef("db.orders", "target.orders", null, null)),
                List.of(),
                List.of(),
                List.of(),
                Configuration.fromMap(pipelineOptions));
    }

    private PipelineDef defaultDefinition() {
        return definition(Map.of("tables", "db.orders"), Map.of("target", "target.orders"), Map.of());
    }

    @Test
    void composesRegularTopologyUsingFactoriesAndCdcContracts() {
        StandalonePipelineExecution execution = assertInstanceOf(
                StandalonePipelineExecution.class,
                COMPOSER.compose(definition(
                        Map.of("tables", "db.orders"),
                        Map.of("target", "target.orders"),
                        Map.of("parallelism", "2",
                               "operator.uid.prefix", "orders",
                               "sink.partitioning.strategy", "TABLE_ID"))));
        StandalonePipelinePlan plan = execution.getPlan();
        assertEquals(2, plan.getParallelism());
        assertEquals(RuntimeExecutionMode.STREAMING, plan.getExecutionMode());
        assertEquals(List.of(StageType.SOURCE, StageType.SCHEMA,
                             StageType.PARTITIONING, StageType.SINK), plan.getStageOrder());
        assertEquals("orders-source", plan.getSource().uid());
        assertEquals("orders-schema-operator", plan.getSchema().uid());
        assertEquals("orders-pre-partition", plan.getPartitioning().uid());
        assertEquals("orders-sink-writer", plan.getSink().uid());
        assertInstanceOf(HashFunctionStrategy.class, plan.getPartitioning().strategy());
        assertEquals(HashFunctionStrategy.TABLE_ID, plan.getPartitioning().strategy());
        assertEquals(1, plan.getSchema().routeRules().size());
        RouteRule route = plan.getSchema().routeRules().get(0);
        assertEquals("db.orders", route.sourceTable);
        assertEquals("target.orders", route.sinkTable);
        assertNotNull(plan.getSource().provider());
        assertNotNull(plan.getSink().provider());
        assertNotNull(plan.getSchema().metadataApplier());
        assertEquals("Yak Ops CDC Event Source: test-source", plan.getSource().name());
        assertEquals("Yak Ops CDC Event Sink: test-sink", plan.getSink().name());
        assertThrows(UnsupportedOperationException.class, execution::execute);
    }

    @Test
    void preservesBatchModeAndSinkDefinedHashProvider() {
        StandalonePipelinePlan plan = ((StandalonePipelineExecution) COMPOSER.compose(
                definition(Map.of("tables", "db.orders"), Map.of("target", "target.orders"),
                        Map.of("execution.runtime-mode", "BATCH")))).getPlan();
        assertEquals(RuntimeExecutionMode.BATCH, plan.getExecutionMode());
        assertEquals(HashFunctionStrategy.SINK_DEFINED, plan.getPartitioning().strategy());
        assertInstanceOf(
                DefaultDataChangeEventHashFunctionProvider.class,
                plan.getPartitioning().hashFunctionProvider());
    }

    @Test
    void rejectsInvalidConfigurationBeforeCreatingSources() {
        assertThrows(IllegalArgumentException.class, () -> COMPOSER.compose(
                definition(Map.of("tables", "db.orders"), Map.of("target", "target.orders"),
                        Map.of("parallelism", "0"))));
        assertThrows(IllegalArgumentException.class, () -> COMPOSER.compose(
                definition(Map.of("tables", "db.orders"), Map.of("target", "target.orders"),
                        Map.of("schema.operator.uid", "custom",
                               "operator.uid.prefix", "jobs"))));
        assertThrows(IllegalArgumentException.class, () -> COMPOSER.compose(
                definition(Map.of("tables", "db.orders"), Map.of("target", "target.orders"),
                        Map.of("operator.uid.prefix", ""))));
    }

    @Test
    void rejectsInvalidFactoryOptionsAndUnknownFactories() {
        assertThrows(IllegalArgumentException.class, () -> COMPOSER.compose(
                definition(Map.of("tables", "db.orders", "unsupported", "x"),
                        Map.of("target", "target.orders"), Map.of())));
        assertThrows(IllegalArgumentException.class, () -> COMPOSER.compose(
                definition(Map.of(), Map.of("target", "target.orders"), Map.of())));
        PipelineDef missing = new PipelineDef(
                new SourceDef("missing-source", null, new Configuration()),
                new SinkDef("test-sink", null, Configuration.fromMap(Map.of("target", "target.orders"))),
                List.of(), List.of(), List.of(), new Configuration());
        assertThrows(IllegalStateException.class, () -> COMPOSER.compose(missing));
    }

    @Test
    void rejectsIncompatibleSinkHashAndUnsupportedTopologies() {
        assertThrows(IllegalArgumentException.class, () -> COMPOSER.compose(
                definition(Map.of("tables", "db.orders"),
                        Map.of("target", "target.orders", "custom-hash", "true"),
                        Map.of("sink.partitioning.strategy", "PRIMARY_KEY"))));
        assertThrows(UnsupportedOperationException.class, () -> COMPOSER.compose(
                definition(Map.of("tables", "db.orders", "parallel-metadata", "true"),
                        Map.of("target", "target.orders"), Map.of())));
        PipelineDef base = defaultDefinition();
        PipelineDef transform = new PipelineDef(
                base.getSource(), base.getSink(), List.of(),
                List.of(new TransformDef("db.orders", "id", null, null, null, null, null, null)),
                List.of(), base.getConfig());
        assertThrows(UnsupportedOperationException.class, () -> COMPOSER.compose(transform));
    }

    public static class TestSourceFactory implements DataSourceFactory {
        private static final ConfigOption<String> TABLES =
                ConfigOptions.key("tables").stringType().noDefaultValue();
        private static final ConfigOption<Boolean> PARALLEL_METADATA =
                ConfigOptions.key("parallel-metadata").booleanType().defaultValue(false);

        @Override public String identifier() { return "test-source"; }
        @Override public Set<ConfigOption<?>> requiredOptions() { return Set.of(TABLES); }
        @Override public Set<ConfigOption<?>> optionalOptions() { return Set.of(PARALLEL_METADATA); }

        @Override
        public DataSource createDataSource(Context context) {
            FactoryHelper.createFactoryHelper(this, context).validate();
            boolean parallel = context.getFactoryConfiguration().get(PARALLEL_METADATA);
            EventSourceProvider provider = new EventSourceProvider() {};
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
        private static final ConfigOption<Boolean> CUSTOM_HASH =
                ConfigOptions.key("custom-hash").booleanType().defaultValue(false);

        @Override public String identifier() { return "test-sink"; }
        @Override public Set<ConfigOption<?>> requiredOptions() { return Set.of(TARGET); }
        @Override public Set<ConfigOption<?>> optionalOptions() { return Set.of(CUSTOM_HASH); }

        @Override
        public DataSink createDataSink(Context context) {
            FactoryHelper.createFactoryHelper(this, context).validate();
            boolean custom = context.getFactoryConfiguration().get(CUSTOM_HASH);
            EventSinkProvider provider = new EventSinkProvider() {};
            MetadataApplier applier = event -> {};
            return new DataSink() {
                @Override public EventSinkProvider getEventSinkProvider() { return provider; }
                @Override public MetadataApplier getMetadataApplier() { return applier; }
                @Override
                public HashFunctionProvider<DataChangeEvent> getDataChangeEventHashFunctionProvider() {
                    if (!custom) {
                        return new DefaultDataChangeEventHashFunctionProvider();
                    }
                    return new HashFunctionProvider<DataChangeEvent>() {
                        @Override
                        public HashFunction<DataChangeEvent> getHashFunction(
                                TableId tableId, Schema schema) {
                            return event -> 0;
                        }
                    };
                }
            };
        }
    }
}
