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
import io.yak.ops.cdc.composer.PipelineExecution;
import io.yak.ops.cdc.composer.definition.SinkDef;
import io.yak.ops.cdc.composer.definition.SourceDef;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Runtime-facing contract for constructing and executing one standalone CDC pipeline.
 *
 * <p>Unlike Flink's StreamExecutionEnvironment, this interface is independent of the Flink
 * DataStream API. A real implementation must materialize and own the SourceReader,
 * SchemaOperator and SinkWriter lifecycles. One environment must be dedicated to one composition.
 *
 * <p>There is intentionally no no-op implementation: a configured pipeline must not be reported
 * as successfully started when no standalone runtime is connected.
 */
public interface StandaloneExecutionEnvironment {

    void setParallelism(int parallelism);

    void setRuntimeMode(RuntimeExecutionMode mode);

    void fromSource(
            SourceDef definition,
            DataSource dataSource,
            EventSourceProvider provider,
            String name,
            int parallelism,
            String uid);

    void addSchemaOperator(
            MetadataApplier metadataApplier,
            List<RouteRule> routeRules,
            RouteMode routeMode,
            SchemaChangeBehavior schemaChangeBehavior,
            ExistingTableSchemaExpansionMode existingTableSchemaExpansionMode,
            Set<SchemaChangeEventType> acceptedSchemaEvolutionTypes,
            Duration rpcTimeout,
            String timeZone,
            String uid,
            boolean batchMode);

    void addPartitioning(
            HashFunctionStrategy strategy,
            HashFunctionProvider<DataChangeEvent> hashFunctionProvider,
            int upstreamParallelism,
            int downstreamParallelism,
            String prePartitionUid,
            String postPartitionUid,
            boolean batchMode);

    void addSink(
            SinkDef definition,
            DataSink dataSink,
            EventSinkProvider provider,
            String name,
            String uid,
            boolean batchMode);

    /** Start the assembled pipeline and return real execution information. */
    PipelineExecution.ExecutionInfo execute(String jobName) throws Exception;
}
