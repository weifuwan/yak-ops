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

import io.yak.ops.cdc.common.configuration.Configuration;
import io.yak.ops.cdc.common.event.DataChangeEvent;
import io.yak.ops.cdc.common.function.HashFunctionProvider;
import io.yak.ops.cdc.common.pipeline.HashFunctionStrategy;
import io.yak.ops.cdc.common.pipeline.PipelineOptions;
import io.yak.ops.cdc.common.pipeline.RuntimeExecutionMode;
import io.yak.ops.cdc.common.sink.DataSink;
import io.yak.ops.cdc.common.sink.DefaultDataChangeEventHashFunctionProvider;
import io.yak.ops.cdc.common.sink.TableIdHashFunctionProvider;
import io.yak.ops.cdc.common.source.DataSource;
import io.yak.ops.cdc.composer.PipelineComposer;
import io.yak.ops.cdc.composer.PipelineExecution;
import io.yak.ops.cdc.composer.definition.PipelineDef;
import io.yak.ops.cdc.composer.standalone.translator.DataSinkTranslator;
import io.yak.ops.cdc.composer.standalone.translator.DataSourceTranslator;
import io.yak.ops.cdc.composer.standalone.translator.OperatorUidGenerator;
import io.yak.ops.cdc.composer.standalone.translator.PartitioningTranslator;
import io.yak.ops.cdc.composer.standalone.translator.SchemaOperatorTranslator;
import java.util.Objects;

import static io.yak.ops.cdc.common.pipeline.PipelineOptions.PIPELINE_OPERATOR_UID_PREFIX;
import static io.yak.ops.cdc.common.pipeline.PipelineOptions.PIPELINE_SCHEMA_OPERATOR_UID;

/**
 * Assembles a standalone CDC pipeline in a supplied execution environment.
 *
 * <p>As in Flink CDC 3.7, the composer assembles the Source, Schema, Partitioning and Sink
 * operators, and the returned PipelineExecution delegates actual execution to that environment.
 * No intermediate StandalonePipelinePlan or Flink DataStream type is required.
 *
 * <p>One composer/environment pair is intended for one pipeline, like Flink's
 * StreamExecutionEnvironment. The environment implementation owns runtime resources and execution.
 */
public final class StandalonePipelineComposer implements PipelineComposer {

    private static final String SCHEMA_OPERATOR_UID_SUFFIX = "schema-operator";

    private final StandaloneExecutionEnvironment env;
    private final DataSourceTranslator sourceTranslator;
    private final DataSinkTranslator sinkTranslator;
    private final SchemaOperatorTranslator schemaOperatorTranslator;
    private final PartitioningTranslator partitioningTranslator;

    public StandalonePipelineComposer(StandaloneExecutionEnvironment env) {
        this(env, Thread.currentThread().getContextClassLoader());
    }

    public StandalonePipelineComposer(
            StandaloneExecutionEnvironment env, ClassLoader classLoader) {
        this(
                env,
                new DataSourceTranslator(Objects.requireNonNull(classLoader, "classLoader")),
                new DataSinkTranslator(classLoader),
                new SchemaOperatorTranslator(),
                new PartitioningTranslator());
    }

    /** Explicit translator injection for tests and alternate connector discovery mechanisms. */
    public StandalonePipelineComposer(
            StandaloneExecutionEnvironment env,
            DataSourceTranslator sourceTranslator,
            DataSinkTranslator sinkTranslator,
            SchemaOperatorTranslator schemaOperatorTranslator,
            PartitioningTranslator partitioningTranslator) {
        this.env = Objects.requireNonNull(env, "env");
        this.sourceTranslator = Objects.requireNonNull(sourceTranslator, "sourceTranslator");
        this.sinkTranslator = Objects.requireNonNull(sinkTranslator, "sinkTranslator");
        this.schemaOperatorTranslator =
                Objects.requireNonNull(schemaOperatorTranslator, "schemaOperatorTranslator");
        this.partitioningTranslator =
                Objects.requireNonNull(partitioningTranslator, "partitioningTranslator");
    }

    @Override
    public PipelineExecution compose(PipelineDef pipelineDef) {
        Objects.requireNonNull(pipelineDef, "pipelineDef");
        Objects.requireNonNull(pipelineDef.getSource(), "pipelineDef.source");
        Objects.requireNonNull(pipelineDef.getSink(), "pipelineDef.sink");
        Configuration config = Objects.requireNonNull(pipelineDef.getConfig(), "pipelineDef.config");

        validatePipelineConfiguration(config);
        rejectUnimplementedTransforms(pipelineDef);

        int parallelism = config.get(PipelineOptions.PIPELINE_PARALLELISM);
        OperatorUidGenerator uidGenerator =
                new OperatorUidGenerator(config.get(PIPELINE_OPERATOR_UID_PREFIX));
        String schemaOperatorUid = config.get(PIPELINE_SCHEMA_OPERATOR_UID);
        if (config.get(PIPELINE_OPERATOR_UID_PREFIX) != null) {
            schemaOperatorUid = uidGenerator.generateUid(SCHEMA_OPERATOR_UID_SUFFIX);
        }

        DataSource dataSource =
                sourceTranslator.createDataSource(pipelineDef.getSource(), config);
        if (dataSource.isParallelMetadataSource()) {
            throw new UnsupportedOperationException(
                    "Distributed schema topology is not supported by StandalonePipelineComposer yet.");
        }
        DataSink dataSink =
                sinkTranslator.createDataSink(pipelineDef.getSink(), config);

        HashFunctionProvider<DataChangeEvent> sinkHashFunction =
                Objects.requireNonNull(
                        dataSink.getDataChangeEventHashFunctionProvider(parallelism),
                        "DataSink returned null HashFunctionProvider");
        validatePartitioningStrategyCompatibility(pipelineDef, sinkHashFunction);
        HashFunctionProvider<DataChangeEvent> hashFunction =
                resolveHashFunctionProvider(config, sinkHashFunction);

        RuntimeExecutionMode runtimeMode =
                config.get(PipelineOptions.PIPELINE_EXECUTION_RUNTIME_MODE);
        boolean isBatchMode = RuntimeExecutionMode.BATCH.equals(runtimeMode);
        env.setParallelism(parallelism);
        env.setRuntimeMode(runtimeMode);

        sourceTranslator.translate(
                pipelineDef.getSource(), dataSource, env, parallelism, uidGenerator);
        schemaOperatorTranslator.translateRegular(
                pipelineDef, dataSink, env, schemaOperatorUid, isBatchMode);
        partitioningTranslator.translateRegular(
                env,
                parallelism,
                parallelism,
                isBatchMode,
                config.get(PipelineOptions.PIPELINE_PARTITIONING_STRATEGY),
                hashFunction,
                uidGenerator);
        sinkTranslator.translate(
                pipelineDef.getSink(), dataSink, env, isBatchMode, uidGenerator);

        return new StandalonePipelineExecution(
                env, config.get(PipelineOptions.PIPELINE_NAME));
    }

    private void validatePipelineConfiguration(Configuration config) {
        int parallelism = config.get(PipelineOptions.PIPELINE_PARALLELISM);
        if (parallelism < 1) {
            throw new IllegalArgumentException("Pipeline parallelism must be greater than zero.");
        }
        String schemaOperatorUid = config.get(PIPELINE_SCHEMA_OPERATOR_UID);
        String operatorUidPrefix = config.get(PIPELINE_OPERATOR_UID_PREFIX);
        if (!Objects.equals(schemaOperatorUid, PIPELINE_SCHEMA_OPERATOR_UID.defaultValue())
                && operatorUidPrefix != null) {
            throw new IllegalArgumentException(
                    String.format("Only one of %s and %s can be set.",
                            PIPELINE_OPERATOR_UID_PREFIX.key(), PIPELINE_SCHEMA_OPERATOR_UID.key()));
        }
        if (operatorUidPrefix != null && operatorUidPrefix.isEmpty()) {
            throw new IllegalArgumentException("operator.uid.prefix cannot be empty.");
        }
    }

    private void rejectUnimplementedTransforms(PipelineDef pipelineDef) {
        if (!pipelineDef.getTransforms().isEmpty()
                || !pipelineDef.getUdfs().isEmpty()
                || !pipelineDef.getModels().isEmpty()) {
            throw new UnsupportedOperationException(
                    "Transform, UDF and model execution are not supported by StandalonePipelineComposer yet.");
        }
    }

    private void validatePartitioningStrategyCompatibility(
            PipelineDef pipelineDef,
            HashFunctionProvider<DataChangeEvent> sinkHashFunction) {
        HashFunctionStrategy strategy =
                pipelineDef.getConfig().get(PipelineOptions.PIPELINE_PARTITIONING_STRATEGY);
        if (!(sinkHashFunction instanceof DefaultDataChangeEventHashFunctionProvider)
                && strategy != HashFunctionStrategy.SINK_DEFINED) {
            throw new IllegalArgumentException(
                    String.format(
                            "Sink type '%s' requires %s=%s for a custom HashFunctionProvider, but found %s.",
                            pipelineDef.getSink().getType(),
                            PipelineOptions.PIPELINE_PARTITIONING_STRATEGY.key(),
                            HashFunctionStrategy.SINK_DEFINED, strategy));
        }
    }

    private HashFunctionProvider<DataChangeEvent> resolveHashFunctionProvider(
            Configuration config, HashFunctionProvider<DataChangeEvent> sinkHashFunction) {
        HashFunctionStrategy strategy = config.get(PipelineOptions.PIPELINE_PARTITIONING_STRATEGY);
        switch (strategy) {
            case SINK_DEFINED:
                return sinkHashFunction;
            case PRIMARY_KEY:
                return new DefaultDataChangeEventHashFunctionProvider();
            case TABLE_ID:
                return new TableIdHashFunctionProvider();
        }
        throw new IllegalStateException("Unexpected hash function strategy: " + strategy);
    }
}
