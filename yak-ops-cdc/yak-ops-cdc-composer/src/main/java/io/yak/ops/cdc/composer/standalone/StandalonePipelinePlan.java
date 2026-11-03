/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
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

import io.yak.ops.cdc.common.event.DataChangeEvent;
import io.yak.ops.cdc.common.event.SchemaChangeEventType;
import io.yak.ops.cdc.common.function.HashFunctionProvider;
import io.yak.ops.cdc.common.pipeline.ExistingTableSchemaExpansionMode;
import io.yak.ops.cdc.common.pipeline.HashFunctionStrategy;
import io.yak.ops.cdc.common.pipeline.RouteMode;
import io.yak.ops.cdc.common.pipeline.RuntimeExecutionMode;
import io.yak.ops.cdc.common.pipeline.SchemaChangeBehavior;
import io.yak.ops.cdc.common.route.RouteRule;
import io.yak.ops.cdc.common.sink.DataSink;
import io.yak.ops.cdc.common.sink.EventSinkProvider;
import io.yak.ops.cdc.common.sink.MetadataApplier;
import io.yak.ops.cdc.common.source.DataSource;
import io.yak.ops.cdc.common.source.EventSourceProvider;
import io.yak.ops.cdc.composer.definition.SinkDef;
import io.yak.ops.cdc.composer.definition.SourceDef;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, Flink-free description of an assembled, regular CDC pipeline.
 *
 * <p>Stages contain CDC contracts and options, not Flink DataStreams or running readers/writers.
 * A standalone runtime must materialize and execute these stages in a later integration.
 */
public final class StandalonePipelinePlan {

    public enum StageType { SOURCE, SCHEMA, PARTITIONING, SINK }

    public record SourceStage(
            SourceDef definition,
            DataSource dataSource,
            EventSourceProvider provider,
            String name,
            String uid) {
        public SourceStage {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(dataSource, "dataSource");
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(name, "name");
        }
    }

    public record SchemaStage(
            MetadataApplier metadataApplier,
            List<RouteRule> routeRules,
            RouteMode routeMode,
            SchemaChangeBehavior schemaChangeBehavior,
            ExistingTableSchemaExpansionMode existingTableSchemaExpansionMode,
            Set<SchemaChangeEventType> acceptedSchemaEvolutionTypes,
            Duration rpcTimeout,
            String timeZone,
            String uid) {
        public SchemaStage {
            Objects.requireNonNull(metadataApplier, "metadataApplier");
            routeRules = List.copyOf(routeRules);
            Objects.requireNonNull(routeMode, "routeMode");
            Objects.requireNonNull(schemaChangeBehavior, "schemaChangeBehavior");
            Objects.requireNonNull(existingTableSchemaExpansionMode, "existingTableSchemaExpansionMode");
            acceptedSchemaEvolutionTypes = Set.copyOf(acceptedSchemaEvolutionTypes);
            Objects.requireNonNull(rpcTimeout, "rpcTimeout");
            Objects.requireNonNull(timeZone, "timeZone");
        }
    }

    public record PartitioningStage(
            HashFunctionStrategy strategy,
            HashFunctionProvider<DataChangeEvent> hashFunctionProvider,
            int downstreamParallelism,
            String uid) {
        public PartitioningStage {
            Objects.requireNonNull(strategy, "strategy");
            Objects.requireNonNull(hashFunctionProvider, "hashFunctionProvider");
            if (downstreamParallelism < 1) {
                throw new IllegalArgumentException("downstreamParallelism must be positive");
            }
        }
    }

    public record SinkStage(
            SinkDef definition,
            DataSink dataSink,
            EventSinkProvider provider,
            String name,
            String uid) {
        public SinkStage {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(dataSink, "dataSink");
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(name, "name");
        }
    }

    private final String name;
    private final RuntimeExecutionMode executionMode;
    private final int parallelism;
    private final SourceStage source;
    private final SchemaStage schema;
    private final PartitioningStage partitioning;
    private final SinkStage sink;

    public StandalonePipelinePlan(
            String name,
            RuntimeExecutionMode executionMode,
            int parallelism,
            SourceStage source,
            SchemaStage schema,
            PartitioningStage partitioning,
            SinkStage sink) {
        this.name = Objects.requireNonNull(name, "name");
        this.executionMode = Objects.requireNonNull(executionMode, "executionMode");
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be positive");
        }
        this.parallelism = parallelism;
        this.source = Objects.requireNonNull(source, "source");
        this.schema = Objects.requireNonNull(schema, "schema");
        this.partitioning = Objects.requireNonNull(partitioning, "partitioning");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    public String getName() { return name; }
    public RuntimeExecutionMode getExecutionMode() { return executionMode; }
    public int getParallelism() { return parallelism; }
    public SourceStage getSource() { return source; }
    public SchemaStage getSchema() { return schema; }
    public PartitioningStage getPartitioning() { return partitioning; }
    public SinkStage getSink() { return sink; }

    public List<StageType> getStageOrder() {
        return List.of(StageType.SOURCE, StageType.SCHEMA, StageType.PARTITIONING, StageType.SINK);
    }
}
