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
import io.yak.ops.cdc.common.factories.DataSourceFactory;
import io.yak.ops.cdc.common.factories.FactoryHelper;
import io.yak.ops.cdc.common.source.DataSource;
import io.yak.ops.cdc.common.source.EventSourceProvider;
import io.yak.ops.cdc.composer.definition.SourceDef;
import io.yak.ops.cdc.composer.utils.FactoryDiscoveryUtils;
import io.yak.ops.cdc.runtime.connectors.RuntimeSourceProvider;
import io.yak.ops.cdc.runtime.datastream.DataStream;
import io.yak.ops.cdc.runtime.datastream.StreamExecutionEnvironment;
import java.util.Objects;

/**
 * Translate a CDC SourceDef to an executable DataStream node.
 *
 * <p>Factory discovery matches Flink CDC; unsupported source provider implementations fail
 * explicitly until migrated onto the standalone SourceOperator contract.
 */
public class DataSourceTranslator {
    private final ClassLoader classLoader;

    public DataSourceTranslator() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public DataSourceTranslator(ClassLoader classLoader) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    public DataSource createDataSource(SourceDef sourceDef, Configuration pipelineConfig) {
        DataSourceFactory factory = FactoryDiscoveryUtils.getFactoryByIdentifier(
                sourceDef.getType(), DataSourceFactory.class, classLoader);
        FactoryHelper.DefaultContext context = new FactoryHelper.DefaultContext(
                sourceDef.getConfig(), pipelineConfig, classLoader);
        return Objects.requireNonNull(
                factory.createDataSource(context), "DataSourceFactory returned null");
    }

    public DataStream<Event> translate(
            SourceDef sourceDef, DataSource dataSource, StreamExecutionEnvironment env,
            OperatorUidGenerator uidGenerator) {
        EventSourceProvider provider = Objects.requireNonNull(
                dataSource.getEventSourceProvider(), "DataSource returned null EventSourceProvider");
        if (!(provider instanceof RuntimeSourceProvider standalone)) {
            throw new IllegalStateException(
                    "Unsupported EventSourceProvider type for standalone runtime: "
                            + provider.getClass().getName());
        }
        return env.fromSource(
                        sourceDef.getName().orElse(
                                "Yak Ops CDC Event Source: " + sourceDef.getType()),
                        standalone::createSourceOperator)
                .uid(uidGenerator.generateUid("source"));
    }
}
