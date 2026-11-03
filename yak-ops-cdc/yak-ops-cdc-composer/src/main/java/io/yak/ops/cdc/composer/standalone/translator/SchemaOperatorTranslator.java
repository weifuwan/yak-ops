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

package io.yak.ops.cdc.composer.standalone.translator;

import io.yak.ops.cdc.common.configuration.Configuration;
import io.yak.ops.cdc.common.pipeline.PipelineOptions;
import io.yak.ops.cdc.common.route.RouteRule;
import io.yak.ops.cdc.common.sink.DataSink;
import io.yak.ops.cdc.common.sink.MetadataApplier;
import io.yak.ops.cdc.composer.definition.PipelineDef;
import io.yak.ops.cdc.composer.definition.RouteDef;
import io.yak.ops.cdc.composer.standalone.StandalonePipelinePlan.SchemaStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Assemble regular Schema stage settings from Flink CDC configuration.
 * Actual SchemaOperator and Schema Registry execution are added by the runtime.
 */
public class SchemaOperatorTranslator {

    public SchemaStage translateRegular(PipelineDef pipelineDef, DataSink dataSink, String uid) {
        Configuration config = pipelineDef.getConfig();
        List<RouteRule> routingRules = new ArrayList<>();
        for (RouteDef route : pipelineDef.getRoute()) {
            routingRules.add(new RouteRule(
                    route.getSourceTable(),
                    route.getSinkTable(),
                    route.getReplaceSymbol().orElse(null)));
        }
        MetadataApplier metadataApplier =
                Objects.requireNonNull(dataSink.getMetadataApplier(), "DataSink returned null MetadataApplier")
                        .setAcceptedSchemaEvolutionTypes(
                                pipelineDef.getSink().getIncludedSchemaEvolutionTypes());
        return new SchemaStage(
                Objects.requireNonNull(metadataApplier, "MetadataApplier returned null"),
                routingRules,
                pipelineDef.getRouteMode(),
                config.get(PipelineOptions.PIPELINE_SCHEMA_CHANGE_BEHAVIOR),
                pipelineDef.getSink().getExistingTableSchemaExpansionMode(),
                pipelineDef.getSink().getIncludedSchemaEvolutionTypes(),
                config.get(PipelineOptions.PIPELINE_SCHEMA_OPERATOR_RPC_TIMEOUT),
                config.get(PipelineOptions.PIPELINE_LOCAL_TIME_ZONE),
                uid);
    }
}
