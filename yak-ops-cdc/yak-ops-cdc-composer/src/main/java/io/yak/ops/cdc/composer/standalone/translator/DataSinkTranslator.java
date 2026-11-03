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
import io.yak.ops.cdc.common.factories.DataSinkFactory;
import io.yak.ops.cdc.common.factories.FactoryHelper;
import io.yak.ops.cdc.common.sink.DataSink;
import io.yak.ops.cdc.common.sink.EventSinkProvider;
import io.yak.ops.cdc.composer.definition.SinkDef;
import io.yak.ops.cdc.composer.standalone.StandalonePipelinePlan.SinkStage;
import io.yak.ops.cdc.composer.utils.FactoryDiscoveryUtils;
import java.util.Objects;

/** Standalone translation of a CDC SinkDef, without Flink SinkOperator/Sink V2 topology. */
public class DataSinkTranslator {

    public DataSink createDataSink(SinkDef sinkDef, Configuration pipelineConfig) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        DataSinkFactory factory =
                FactoryDiscoveryUtils.getFactoryByIdentifier(
                        sinkDef.getType(), DataSinkFactory.class, classLoader);
        FactoryHelper.DefaultContext context =
                new FactoryHelper.DefaultContext(
                        sinkDef.getConfig(), pipelineConfig, classLoader);
        return Objects.requireNonNull(factory.createDataSink(context), "DataSinkFactory returned null");
    }

    public SinkStage translate(
            SinkDef sinkDef, DataSink dataSink, OperatorUidGenerator uidGenerator) {
        EventSinkProvider provider = Objects.requireNonNull(
                dataSink.getEventSinkProvider(), "DataSink returned a null EventSinkProvider");
        String name = sinkDef.getName().orElse("Yak Ops CDC Event Sink: " + sinkDef.getType());
        return new SinkStage(sinkDef, dataSink, provider, name, uidGenerator.generateUid("sink-writer"));
    }
}
