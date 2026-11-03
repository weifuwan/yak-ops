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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.common.configuration.ConfigOption;
import io.yak.ops.cdc.common.configuration.ConfigOptions;
import io.yak.ops.cdc.common.configuration.Configuration;
import io.yak.ops.cdc.common.event.DataChangeEvent;
import io.yak.ops.cdc.common.event.SchemaChangeEventType;
import io.yak.ops.cdc.common.event.TableId;
import io.yak.ops.cdc.common.factories.DataSinkFactory;
import io.yak.ops.cdc.common.factories.DataSourceFactory;
import io.yak.ops.cdc.common.factories.FactoryHelper;
import io.yak.ops.cdc.common.function.HashFunction;
import io.yak.ops.cdc.common.function.HashFunctionProvider;
import io.yak.ops.cdc.common.pipeline.ExistingTableSchemaExpansionMode;
import io.yak.ops.cdc.common.pipeline.HashFunctionStrategy;
import io.yak.ops.cdc.common.pipeline.RouteMode;
import io.yak.ops.cdc.common.pipeline.RuntimeExecutionMode;
import io.yak.ops.cdc.common.pipeline.SchemaChangeBehavior;
import io.yak.ops.cdc.common.route.RouteRule;
import io.yak.ops.cdc.common.schema.Schema;
import io.yak.ops.cdc.common.sink.DataSink;
import io.yak.ops.cdc.common.sink.DefaultDataChangeEventHashFunctionProvider;
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
import io.yak.ops.cdc.composer.standalone.translator.DataSourceTranslator;
import io.yak.ops.cdc.composer.standalone.translator.DataSinkTranslator;
import io.yak.ops.cdc.composer.standalone.translator.OperatorUidGenerator;
import io.yak.ops.cdc.composer.standalone.translator.PartitioningTranslator;
import io.yak.ops.cdc.composer.standalone.translator.SchemaOperatorTranslator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StandalonePipelineComposerTest {

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

    private PipelineExecution compose(PipelineDef def) {
        return new StandalonePipelineComposer(new RecordingEnvironment()).compose(def);
    }

    @Test
    void assemblesRegularTopologyDirectlyIntoInjectedEnvironment() throws Exception {
        RecordingEnvironment env = new RecordingEnvironment();
        StandalonePipelineComposer composer = new StandalonePipelineComposer(env);
        StandalonePipelineExecution execution = assertInstanceOf(
                StandalonePipelineExecution.class,
                composer.compose(definition(
                        Map.of("tables", "db.orders"),
                        Map.of("target", "target.orders"),
                        Map.of("parallelism", "2",
                               "operator.uid.prefix", "orders",
                               "sink.partitioning.strategy", "TABLE_ID",
                               "name", "Order replication"))));

        assertEquals(2, env.parallelism);
        assertEquals(RuntimeExecutionMode.STREAMING, env.runtimeMode);
        assertEquals(List.of("source", "schema", "partitioning", "sink"), env.stages);
        assertEquals("orders-source", env.sourceUid);
        assertEquals("orders-schema-operator", env.schemaUid);
        assertEquals("orders-pre-partition", env.prePartitionUid);
        assertEquals("orders-post-partition", env.postPartitionUid);
        assertEquals("orders-sink-writer", env.sinkUid);
        assertEquals(HashFunctionStrategy.TABLE_ID, env.strategy);
        assertEquals(2, env.sourceParallelism);
        assertEquals(2, env.downstreamParallelism);
        assertEquals(1, env.routeRules.size());
        assertEquals("db.orders", env.routeRules.get(0).sourceTable);
        assertEquals("target.orders", env.routeRules.get(0).sinkTable);
        assertNotNull(env.sourceProvider);
        assertNotNull(env.sinkProvider);
        assertNotNull(env.metadataApplier);
        assertEquals("Yak Ops CDC Event Source: test-source", env.sourceName);
        assertEquals("Yak Ops CDC Event Sink: test-sink", env.sinkName);
        assertEquals(RouteMode.ALL_MATCH, env.routeMode);
        PipelineExecution.ExecutionInfo info = execution.execute();
        assertEquals("recorded-test-job", info.getId());
        assertEquals("Order replication", info.getDescription());
        assertEquals("Order replication", env.executedJobName);
        assertEquals(1, env.executionCalls);
    }

    @Test
    void injectsSourceTranslatorAndAllowsExplicitClassLoader() {
        RecordingEnvironment env = new RecordingEnvironment();
        class TrackingSourceTranslator extends DataSourceTranslator {
            boolean translated;
            @Override
            public void translate(
                    SourceDef definition,
                    DataSource dataSource,
                    StandaloneExecutionEnvironment environment,
                    int parallelism,
                    OperatorUidGenerator uidGenerator) {
                translated = true;
                super.translate(definition, dataSource, environment, parallelism, uidGenerator);
            }
        }
        TrackingSourceTranslator sourceTranslator = new TrackingSourceTranslator();
        StandalonePipelineComposer composer = new StandalonePipelineComposer(
                env,
                sourceTranslator,
                new DataSinkTranslator(),
                new SchemaOperatorTranslator(),
                new PartitioningTranslator());
        composer.compose(defaultDefinition());
        assertTrue(sourceTranslator.translated);

        RecordingEnvironment another = new RecordingEnvironment();
        new StandalonePipelineComposer(another, TestSourceFactory.class.getClassLoader())
                .compose(defaultDefinition());
        assertEquals(List.of("source", "schema", "partitioning", "sink"), another.stages);
    }

    @Test
    void passesBatchModeAndSinkDefinedHashProviderToEnvironment() {
        RecordingEnvironment env = new RecordingEnvironment();
        new StandalonePipelineComposer(env).compose(
                definition(Map.of("tables", "db.orders"), Map.of("target", "target.orders"),
                        Map.of("execution.runtime-mode", "BATCH")));
        assertEquals(RuntimeExecutionMode.BATCH, env.runtimeMode);
        assertTrue(env.schemaBatchMode);
        assertTrue(env.partitionBatchMode);
        assertTrue(env.sinkBatchMode);
        assertEquals(HashFunctionStrategy.SINK_DEFINED, env.strategy);
        assertInstanceOf(
                DefaultDataChangeEventHashFunctionProvider.class, env.hashFunctionProvider);
    }

    @Test
    void invalidArgumentsAndDefinitionsFailBeforeRuntimeRegistration() {
        assertThrows(NullPointerException.class,
                () -> new StandalonePipelineComposer((StandaloneExecutionEnvironment) null));
        RecordingEnvironment env = new RecordingEnvironment();
        assertThrows(NullPointerException.class, () -> new StandalonePipelineComposer(
                env, null, new DataSinkTranslator(),
                new SchemaOperatorTranslator(), new PartitioningTranslator()));
        assertThrows(IllegalArgumentException.class, () -> new StandalonePipelineComposer(env).compose(
                definition(Map.of("tables", "db.orders"), Map.of("target", "target.orders"),
                        Map.of("parallelism", "0"))));
        assertEquals(List.of(), env.stages);
        assertThrows(IllegalArgumentException.class, () -> compose(
                definition(Map.of("tables", "db.orders"), Map.of("target", "target.orders"),
                        Map.of("schema.operator.uid", "custom",
                               "operator.uid.prefix", "jobs"))));
        assertThrows(IllegalArgumentException.class, () -> compose(
                definition(Map.of("tables", "db.orders"), Map.of("target", "target.orders"),
                        Map.of("operator.uid.prefix", ""))));
    }

    @Test
    void rejectsInvalidFactoryOptionsAndUnknownFactories() {
        assertThrows(IllegalArgumentException.class, () -> compose(
                definition(Map.of("tables", "db.orders", "unsupported", "x"),
                        Map.of("target", "target.orders"), Map.of())));
        assertThrows(IllegalArgumentException.class, () -> compose(
                definition(Map.of(), Map.of("target", "target.orders"), Map.of())));
        PipelineDef missing = new PipelineDef(
                new SourceDef("missing-source", null, new Configuration()),
                new SinkDef("test-sink", null,
                        Configuration.fromMap(Map.of("target", "target.orders"))),
                List.of(), List.of(), List.of(), new Configuration());
        assertThrows(IllegalStateException.class, () -> compose(missing));
    }

    @Test
    void rejectsIncompatibleSinkHashAndUnsupportedTopologies() {
        assertThrows(IllegalArgumentException.class, () -> compose(
                definition(Map.of("tables", "db.orders"),
                        Map.of("target", "target.orders", "custom-hash", "true"),
                        Map.of("sink.partitioning.strategy", "PRIMARY_KEY"))));
        assertThrows(UnsupportedOperationException.class, () -> compose(
                definition(Map.of("tables", "db.orders", "parallel-metadata", "true"),
                        Map.of("target", "target.orders"), Map.of())));
        PipelineDef base = defaultDefinition();
        PipelineDef transform = new PipelineDef(
                base.getSource(), base.getSink(), List.of(),
                List.of(new TransformDef("db.orders", "id", null, null, null, null, null, null)),
                List.of(), base.getConfig());
        assertThrows(UnsupportedOperationException.class, () -> compose(transform));
    }

    @Test
    void runtimeFailureIsPropagatedRatherThanReportedAsSuccess() {
        RecordingEnvironment env = new RecordingEnvironment();
        env.failOnExecute = true;
        PipelineExecution execution =
                new StandalonePipelineComposer(env).compose(defaultDefinition());
        UnsupportedOperationException failure =
                assertThrows(UnsupportedOperationException.class, execution::execute);
        assertTrue(failure.getMessage().contains("runtime"));
    }

    /**
     * Test-only environment. Does not run any CDC readers or writers.
     * Real runtime implementations must own actual execution.
     */
    private static class RecordingEnvironment implements StandaloneExecutionEnvironment {
        final List<String> stages = new ArrayList<>();
        int parallelism;
        RuntimeExecutionMode runtimeMode;
        String sourceUid;
        String schemaUid;
        String prePartitionUid;
        String postPartitionUid;
        String sinkUid;
        int sourceParallelism;
        int downstreamParallelism;
        String sourceName;
        String sinkName;
        EventSourceProvider sourceProvider;
        EventSinkProvider sinkProvider;
        MetadataApplier metadataApplier;
        List<RouteRule> routeRules;
        RouteMode routeMode;
        HashFunctionStrategy strategy;
        HashFunctionProvider<DataChangeEvent> hashFunctionProvider;
        boolean schemaBatchMode;
        boolean partitionBatchMode;
        boolean sinkBatchMode;
        String executedJobName;
        int executionCalls;
        boolean failOnExecute;

        @Override public void setParallelism(int parallelism) { this.parallelism = parallelism; }
        @Override public void setRuntimeMode(RuntimeExecutionMode mode) { this.runtimeMode = mode; }

        @Override
        public void fromSource(SourceDef definition, DataSource dataSource,
                               EventSourceProvider provider, String name,
                               int parallelism, String uid) {
            stages.add("source");
            this.sourceUid = uid;
            this.sourceParallelism = parallelism;
            this.sourceName = name;
            this.sourceProvider = provider;
        }

        @Override
        public void addSchemaOperator(
                MetadataApplier metadataApplier,
                List<RouteRule> rules,
                RouteMode routeMode,
                SchemaChangeBehavior behavior,
                ExistingTableSchemaExpansionMode existingTableSchemaExpansionMode,
                Set<SchemaChangeEventType> accepted,
                Duration timeout,
                String timezone,
                String uid,
                boolean isBatchMode) {
            stages.add("schema");
            this.metadataApplier = metadataApplier;
            this.routeRules = List.copyOf(rules);
            this.routeMode = routeMode;
            this.schemaUid = uid;
            this.schemaBatchMode = isBatchMode;
        }

        @Override
        public void addPartitioning(
                HashFunctionStrategy strategy,
                HashFunctionProvider<DataChangeEvent> hashFunctionProvider,
                int upstreamParallelism,
                int downstreamParallelism,
                String prePartitionUid,
                String postPartitionUid,
                boolean isBatchMode) {
            stages.add("partitioning");
            this.strategy = strategy;
            this.hashFunctionProvider = hashFunctionProvider;
            this.downstreamParallelism = downstreamParallelism;
            this.prePartitionUid = prePartitionUid;
            this.postPartitionUid = postPartitionUid;
            this.partitionBatchMode = isBatchMode;
        }

        @Override
        public void addSink(
                SinkDef definition,
                DataSink dataSink,
                EventSinkProvider provider,
                String name,
                String uid,
                boolean isBatchMode) {
            stages.add("sink");
            this.sinkProvider = provider;
            this.sinkName = name;
            this.sinkUid = uid;
            this.sinkBatchMode = isBatchMode;
        }

        @Override
        public PipelineExecution.ExecutionInfo execute(String jobName) {
            executionCalls++;
            executedJobName = jobName;
            if (failOnExecute) {
                throw new UnsupportedOperationException("Standalone runtime is not connected");
            }
            return new PipelineExecution.ExecutionInfo("recorded-test-job", jobName);
        }
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
