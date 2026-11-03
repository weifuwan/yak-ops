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

package io.yak.ops.cdc.composer.standalone.translator;

import io.yak.ops.cdc.common.configuration.Configuration;
import io.yak.ops.cdc.common.factories.DataSinkFactory;
import io.yak.ops.cdc.common.factories.FactoryHelper;
import io.yak.ops.cdc.common.sink.DataSink;
import io.yak.ops.cdc.common.sink.EventSinkProvider;
import io.yak.ops.cdc.composer.definition.SinkDef;
import io.yak.ops.cdc.composer.standalone.StandaloneExecutionEnvironment;
import io.yak.ops.cdc.composer.utils.FactoryDiscoveryUtils;
import java.util.Objects;

/** Standalone Sink translator: create DataSink through SPI and register it with the runtime. */
public class DataSinkTranslator {
    private final ClassLoader classLoader;

    public DataSinkTranslator() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public DataSinkTranslator(ClassLoader classLoader) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    public DataSink createDataSink(SinkDef sinkDef, Configuration pipelineConfig) {
        DataSinkFactory factory = FactoryDiscoveryUtils.getFactoryByIdentifier(
                sinkDef.getType(), DataSinkFactory.class, classLoader);
        FactoryHelper.DefaultContext context = new FactoryHelper.DefaultContext(
                sinkDef.getConfig(), pipelineConfig, classLoader);
        return Objects.requireNonNull(
                factory.createDataSink(context), "DataSinkFactory returned null");
    }

    public void translate(
            SinkDef sinkDef,
            DataSink dataSink,
            StandaloneExecutionEnvironment env,
            boolean isBatchMode,
            OperatorUidGenerator uidGenerator) {
        EventSinkProvider provider = Objects.requireNonNull(
                dataSink.getEventSinkProvider(), "DataSink returned null EventSinkProvider");
        env.addSink(
                sinkDef, dataSink, provider,
                sinkDef.getName().orElse("Yak Ops CDC Event Sink: " + sinkDef.getType()),
                uidGenerator.generateUid("sink-writer"),
                isBatchMode);
    }
}
