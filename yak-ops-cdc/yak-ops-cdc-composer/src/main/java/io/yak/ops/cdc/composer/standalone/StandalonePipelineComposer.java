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
import io.yak.ops.cdc.common.event.Event;
import io.yak.ops.cdc.common.pipeline.ExistingTableSchemaExpansionMode;
import io.yak.ops.cdc.common.pipeline.HashFunctionStrategy;
import io.yak.ops.cdc.common.pipeline.PipelineOptions;
import io.yak.ops.cdc.common.pipeline.RuntimeExecutionMode;
import io.yak.ops.cdc.common.pipeline.SchemaChangeBehavior;
import io.yak.ops.cdc.common.sink.DataSink;
import io.yak.ops.cdc.common.sink.MetadataApplier;
import io.yak.ops.cdc.common.source.DataSource;
import io.yak.ops.cdc.composer.PipelineComposer;
import io.yak.ops.cdc.composer.PipelineExecution;
import io.yak.ops.cdc.composer.definition.PipelineDef;
import io.yak.ops.cdc.composer.standalone.translator.DataSinkTranslator;
import io.yak.ops.cdc.composer.standalone.translator.DataSourceTranslator;
import io.yak.ops.cdc.composer.standalone.translator.OperatorUidGenerator;
import io.yak.ops.cdc.composer.standalone.translator.PartitioningTranslator;
import io.yak.ops.cdc.composer.standalone.translator.SchemaOperatorTranslator;
import io.yak.ops.cdc.composer.standalone.translator.TransformTranslator;
import io.yak.ops.cdc.runtime.datastream.DataStream;
import io.yak.ops.cdc.runtime.datastream.StreamExecutionEnvironment;
import java.util.Objects;

import static io.yak.ops.cdc.common.pipeline.PipelineOptions.PIPELINE_OPERATOR_UID_PREFIX;

/**
 * Composes a Flink CDC-style pipeline using the single-JVM DataStream runtime.
 *
 * <p>The regular topology follows Flink CDC 3.7: Source -> PreTransform -> PostTransform ->
 * SchemaOperator -> PrePartition -> PostPartition -> Sink. The partitioning stages preserve
 * ordering in the one-partition runtime. Distributed metadata, multiple subtasks and advanced
 * transform execution remain intentionally unsupported.
 *
 * <p>Each environment represents exactly one pipeline.
 */
public final class StandalonePipelineComposer implements PipelineComposer {
    private static final String SCHEMA_OPERATOR_UID_SUFFIX = "schema-operator";

    private final StreamExecutionEnvironment env;
    private final DataSourceTranslator sourceTranslator;
    private final DataSinkTranslator sinkTranslator;
    private final TransformTranslator transformTranslator;

    public StandalonePipelineComposer(StreamExecutionEnvironment env) {
        this(env, Thread.currentThread().getContextClassLoader());
    }

    public StandalonePipelineComposer(StreamExecutionEnvironment env, ClassLoader classLoader) {
        this(env,
                new DataSourceTranslator(Objects.requireNonNull(classLoader, "classLoader")),
                new DataSinkTranslator(classLoader),
                new TransformTranslator());
    }

    public StandalonePipelineComposer(
            StreamExecutionEnvironment env,
            DataSourceTranslator sourceTranslator,
            DataSinkTranslator sinkTranslator,
            TransformTranslator transformTranslator) {
        this.env = Objects.requireNonNull(env, "env");
        this.sourceTranslator = Objects.requireNonNull(sourceTranslator, "sourceTranslator");
        this.sinkTranslator = Objects.requireNonNull(sinkTranslator, "sinkTranslator");
        this.transformTranslator = Objects.requireNonNull(transformTranslator, "transformTranslator");
    }

    @Override
    public PipelineExecution compose(PipelineDef pipelineDef) {
        Objects.requireNonNull(pipelineDef, "pipelineDef");
        Objects.requireNonNull(pipelineDef.getSource(), "source");
        Objects.requireNonNull(pipelineDef.getSink(), "sink");
        Configuration config = Objects.requireNonNull(pipelineDef.getConfig(), "config");

        translate(env, pipelineDef);

        // Materialize, validate and seal the complete graph before handing it to the runner.
        env.getStreamGraph();
        return new StandalonePipelineExecution(env, config.get(PipelineOptions.PIPELINE_NAME));
    }

    /** Follow the same operator-translation order as FlinkPipelineComposer.translate(). */
    private void translate(StreamExecutionEnvironment env, PipelineDef pipelineDef) {
        Configuration pipelineConfig = pipelineDef.getConfig();
        int parallelism = pipelineConfig.get(PipelineOptions.PIPELINE_PARALLELISM);
        boolean isBatchMode = RuntimeExecutionMode.BATCH.equals(
                pipelineConfig.get(PipelineOptions.PIPELINE_EXECUTION_RUNTIME_MODE));
        SchemaChangeBehavior schemaChangeBehavior =
                pipelineConfig.get(PipelineOptions.PIPELINE_SCHEMA_CHANGE_BEHAVIOR);

        validatePipelineConfiguration(pipelineDef);

        String operatorUidPrefix = pipelineConfig.get(PIPELINE_OPERATOR_UID_PREFIX);
        OperatorUidGenerator uidGenerator = new OperatorUidGenerator(operatorUidPrefix);
        String schemaOperatorUid = operatorUidPrefix == null
                ? pipelineConfig.get(PipelineOptions.PIPELINE_SCHEMA_OPERATOR_UID)
                : uidGenerator.generateUid(SCHEMA_OPERATOR_UID_SUFFIX);

        // Initialize translators and connector instances before registering graph nodes.
        SchemaOperatorTranslator schemaTranslator = new SchemaOperatorTranslator();
        PartitioningTranslator partitioningTranslator = new PartitioningTranslator();
        DataSource dataSource =
                sourceTranslator.createDataSource(pipelineDef.getSource(), pipelineConfig);
        DataSink dataSink =
                sinkTranslator.createDataSink(pipelineDef.getSink(), pipelineConfig);

        if (dataSource.isParallelMetadataSource()) {
            throw new UnsupportedOperationException(
                    "Distributed metadata topology (Partitioning -> SchemaOperator) "
                            + "is not supported in the single-partition runtime");
        }
        MetadataApplier metadataApplier = Objects.requireNonNull(
                dataSink.getMetadataApplier(), "DataSink returned null MetadataApplier");
        metadataApplier = Objects.requireNonNull(
                metadataApplier.setAcceptedSchemaEvolutionTypes(
                        pipelineDef.getSink().getIncludedSchemaEvolutionTypes()),
                "MetadataApplier returned null after schema evolution configuration");

        // Source -> PreTransform -> PostTransform
        DataStream<Event> stream = sourceTranslator.translate(
                pipelineDef.getSource(), dataSource, env, uidGenerator);
        stream = transformTranslator.translatePreTransform(stream, pipelineDef.getTransforms());
        stream = transformTranslator.translatePostTransform(
                stream, pipelineDef.getTransforms(), uidGenerator);

        // Regular Flink CDC topology: PostTransform -> SchemaOperator -> Partitioning -> Sink.
        stream = schemaTranslator.translateRegular(
                stream, parallelism, isBatchMode, metadataApplier, schemaChangeBehavior,
                schemaOperatorUid);
        stream = partitioningTranslator.translateRegular(
                stream, parallelism, parallelism, isBatchMode, uidGenerator);
        sinkTranslator.translate(pipelineDef.getSink(), stream, dataSink, uidGenerator);
    }

    private void validatePipelineConfiguration(PipelineDef pipelineDef) {
        Configuration config = pipelineDef.getConfig();
        if (config.get(PipelineOptions.PIPELINE_PARALLELISM) != 1) {
            throw new UnsupportedOperationException(
                    "Standalone runtime supports only parallelism=1");
        }
        if (!pipelineDef.getRoute().isEmpty()) {
            throw new UnsupportedOperationException(
                    "Table routing is not yet supported by the standalone SchemaOperator");
        }
        if (!pipelineDef.getUdfs().isEmpty() || !pipelineDef.getModels().isEmpty()) {
            throw new UnsupportedOperationException("UDF and model execution are not yet supported");
        }
        if (config.get(PipelineOptions.PIPELINE_TRANSFORM_ASYNC_EXECUTION_ENABLED)) {
            throw new UnsupportedOperationException(
                    "Asynchronous post-transform execution is not supported");
        }
        if (config.get(PipelineOptions.PIPELINE_PARTITIONING_STRATEGY)
                != HashFunctionStrategy.SINK_DEFINED) {
            throw new UnsupportedOperationException(
                    "Explicit partitioning strategies require multiple partitions");
        }
        if (pipelineDef.getSink().getExistingTableSchemaExpansionMode()
                != ExistingTableSchemaExpansionMode.DISABLED) {
            throw new UnsupportedOperationException(
                    "Existing target table schema expansion is not implemented in standalone");
        }

        String uidPrefix = config.get(PIPELINE_OPERATOR_UID_PREFIX);
        String schemaOperatorUid = config.get(PipelineOptions.PIPELINE_SCHEMA_OPERATOR_UID);
        if (uidPrefix != null && uidPrefix.isEmpty()) {
            throw new IllegalArgumentException("operator.uid.prefix cannot be empty");
        }
        if (uidPrefix != null && !Objects.equals(schemaOperatorUid,
                PipelineOptions.PIPELINE_SCHEMA_OPERATOR_UID.defaultValue())) {
            throw new IllegalArgumentException(
                    "Only one of operator.uid.prefix and schema.operator.uid may be set");
        }
    }
}
