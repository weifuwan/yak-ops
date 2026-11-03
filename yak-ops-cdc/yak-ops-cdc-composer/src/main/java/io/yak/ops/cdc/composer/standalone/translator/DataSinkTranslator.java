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
import io.yak.ops.cdc.common.event.Event;
import io.yak.ops.cdc.common.factories.DataSinkFactory;
import io.yak.ops.cdc.common.factories.FactoryHelper;
import io.yak.ops.cdc.common.sink.DataSink;
import io.yak.ops.cdc.common.sink.EventSinkProvider;
import io.yak.ops.cdc.composer.definition.SinkDef;
import io.yak.ops.cdc.composer.utils.FactoryDiscoveryUtils;
import io.yak.ops.cdc.runtime.connectors.RuntimeSinkProvider;
import io.yak.ops.cdc.runtime.datastream.DataStream;
import java.util.Objects;

/** Translate CDC SinkDef into a real terminal node attached to its input DataStream. */
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
            SinkDef sinkDef, DataStream<Event> input, DataSink dataSink,
            OperatorUidGenerator uidGenerator) {
        EventSinkProvider provider = Objects.requireNonNull(
                dataSink.getEventSinkProvider(), "DataSink returned null EventSinkProvider");
        if (!(provider instanceof RuntimeSinkProvider standalone)) {
            throw new IllegalStateException(
                    "Unsupported EventSinkProvider type for standalone runtime: "
                            + provider.getClass().getName());
        }
        input.addSink(
                sinkDef.getName().orElse("Yak Ops CDC Event Sink: " + sinkDef.getType()),
                standalone::createSinkOperator,
                uidGenerator.generateUid("sink-writer"));
    }
}
