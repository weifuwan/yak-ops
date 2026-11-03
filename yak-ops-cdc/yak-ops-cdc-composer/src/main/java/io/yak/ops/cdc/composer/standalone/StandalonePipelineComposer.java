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
import io.yak.ops.cdc.common.pipeline.HashFunctionStrategy;
import io.yak.ops.cdc.common.pipeline.PipelineOptions;
import io.yak.ops.cdc.common.source.DataSource;
import io.yak.ops.cdc.common.sink.DataSink;
import io.yak.ops.cdc.composer.PipelineComposer;
import io.yak.ops.cdc.composer.PipelineExecution;
import io.yak.ops.cdc.composer.definition.PipelineDef;
import io.yak.ops.cdc.composer.standalone.translator.DataSinkTranslator;
import io.yak.ops.cdc.composer.standalone.translator.DataSourceTranslator;
import io.yak.ops.cdc.composer.standalone.translator.OperatorUidGenerator;
import io.yak.ops.cdc.composer.standalone.translator.TransformTranslator;
import io.yak.ops.cdc.runtime.datastream.DataStream;
import io.yak.ops.cdc.runtime.datastream.StreamExecutionEnvironment;
import java.util.Objects;

import static io.yak.ops.cdc.common.pipeline.PipelineOptions.PIPELINE_OPERATOR_UID_PREFIX;

/**
 * Flink CDC-style composer backed by the minimal single-threaded local DataStream API.
 *
 * <p>Scope is Source -> optional PreTransform -> optional PostTransform -> Sink. Schema routing,
 * parallel metadata and partitioning are explicitly unsupported until implemented in runtime.
 * A composer/environment pair represents exactly one pipeline.
 */
public final class StandalonePipelineComposer implements PipelineComposer {
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
    public PipelineExecution compose(PipelineDef definition) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(definition.getSource(), "source");
        Objects.requireNonNull(definition.getSink(), "sink");
        Configuration config = Objects.requireNonNull(definition.getConfig(), "config");
        validateSupportedConfiguration(definition);

        OperatorUidGenerator uidGenerator =
                new OperatorUidGenerator(config.get(PIPELINE_OPERATOR_UID_PREFIX));
        DataSource dataSource = sourceTranslator.createDataSource(definition.getSource(), config);
        if (dataSource.isParallelMetadataSource()) {
            throw new UnsupportedOperationException(
                    "Parallel metadata sources require a distributed schema topology");
        }
        DataSink dataSink = sinkTranslator.createDataSink(definition.getSink(), config);

        DataStream<Event> stream =
                sourceTranslator.translate(definition.getSource(), dataSource, env, uidGenerator);
        stream = transformTranslator.translatePreTransform(stream, definition.getTransforms());
        stream = transformTranslator.translatePostTransform(
                stream, definition.getTransforms(), uidGenerator);
        sinkTranslator.translate(definition.getSink(), stream, dataSink, uidGenerator);

        // Validate and seal the whole graph before giving it to the execution adapter.
        env.getStreamGraph();
        return new StandalonePipelineExecution(env, config.get(PipelineOptions.PIPELINE_NAME));
    }

    private void validateSupportedConfiguration(PipelineDef definition) {
        Configuration config = definition.getConfig();
        if (config.get(PipelineOptions.PIPELINE_PARALLELISM) != 1) {
            throw new UnsupportedOperationException(
                    "Standalone runtime v1 supports only parallelism=1");
        }
        if (!definition.getRoute().isEmpty()) {
            throw new UnsupportedOperationException(
                    "Table routing requires the SchemaOperator, which is not yet supported");
        }
        if (!definition.getUdfs().isEmpty() || !definition.getModels().isEmpty()) {
            throw new UnsupportedOperationException(
                    "UDF and model execution are not yet supported");
        }
        if (config.get(PipelineOptions.PIPELINE_PARTITIONING_STRATEGY)
                != HashFunctionStrategy.SINK_DEFINED) {
            throw new UnsupportedOperationException(
                    "Explicit partitioning strategies require a partitioning operator");
        }
        String prefix = config.get(PIPELINE_OPERATOR_UID_PREFIX);
        if (prefix != null && prefix.isEmpty()) {
            throw new IllegalArgumentException("operator.uid.prefix cannot be empty");
        }
        if (!Objects.equals(
                config.get(PipelineOptions.PIPELINE_SCHEMA_OPERATOR_UID),
                PipelineOptions.PIPELINE_SCHEMA_OPERATOR_UID.defaultValue())) {
            throw new UnsupportedOperationException(
                    "SchemaOperator UID cannot be used before SchemaOperator is implemented");
        }
    }
}
