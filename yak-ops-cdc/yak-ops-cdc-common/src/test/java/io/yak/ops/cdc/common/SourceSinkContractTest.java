/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.cdc.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.common.configuration.ConfigOption;
import io.yak.ops.cdc.common.configuration.Configuration;
import io.yak.ops.cdc.common.event.ChangeEvent;
import io.yak.ops.cdc.common.event.Event;
import io.yak.ops.cdc.common.event.SchemaChangeEvent;
import io.yak.ops.cdc.common.event.SchemaChangeEventType;
import io.yak.ops.cdc.common.event.TableId;
import io.yak.ops.cdc.common.exceptions.SchemaEvolveException;
import io.yak.ops.cdc.common.factories.DataSinkFactory;
import io.yak.ops.cdc.common.factories.DataSourceFactory;
import io.yak.ops.cdc.common.factories.Factory;
import io.yak.ops.cdc.common.sink.DataSink;
import io.yak.ops.cdc.common.sink.EventSinkProvider;
import io.yak.ops.cdc.common.sink.MetadataApplier;
import io.yak.ops.cdc.common.source.DataSource;
import io.yak.ops.cdc.common.source.EventSourceProvider;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Contract tests for the Flink-free, standalone CDC source and sink SPI. */
class SourceSinkContractTest {

    @Test
    void eventHierarchyRemainsShared() {
        assertTrue(Event.class.isAssignableFrom(ChangeEvent.class));
        assertTrue(ChangeEvent.class.isAssignableFrom(SchemaChangeEvent.class));
        assertEquals("db.orders", TableId.tableId("db", "orders").identifier());
    }

    @Test
    void factoriesShareConfigurationAndProduceSourceAndSinkProviders() {
        Configuration factoryConfig = new Configuration();
        Configuration pipelineConfig = new Configuration();
        Factory.Context context = new Factory.Context() {
            @Override
            public Configuration getFactoryConfiguration() {
                return factoryConfig;
            }

            @Override
            public Configuration getPipelineConfiguration() {
                return pipelineConfig;
            }

            @Override
            public ClassLoader getClassLoader() {
                return getClass().getClassLoader();
            }
        };

        EventSourceProvider sourceProvider = new EventSourceProvider() {};
        DataSourceFactory sourceFactory = new DataSourceFactory() {
            @Override
            public String identifier() {
                return "test-source";
            }

            @Override
            public Set<ConfigOption<?>> requiredOptions() {
                return Collections.emptySet();
            }

            @Override
            public Set<ConfigOption<?>> optionalOptions() {
                return Collections.emptySet();
            }

            @Override
            public DataSource createDataSource(Context context) {
                assertSame(factoryConfig, context.getFactoryConfiguration());
                assertSame(pipelineConfig, context.getPipelineConfiguration());
                return () -> sourceProvider;
            }
        };
        DataSource source = sourceFactory.createDataSource(context);
        assertSame(sourceProvider, source.getEventSourceProvider());
        assertFalse(source.isParallelMetadataSource());

        EventSinkProvider sinkProvider = new EventSinkProvider() {};
        MetadataApplier applier = event -> {};
        DataSinkFactory sinkFactory = new DataSinkFactory() {
            @Override
            public String identifier() {
                return "test-sink";
            }

            @Override
            public Set<ConfigOption<?>> requiredOptions() {
                return Collections.emptySet();
            }

            @Override
            public Set<ConfigOption<?>> optionalOptions() {
                return Collections.emptySet();
            }

            @Override
            public DataSink createDataSink(Context context) {
                assertSame(pipelineConfig, context.getPipelineConfiguration());
                return new DataSink() {
                    @Override
                    public EventSinkProvider getEventSinkProvider() {
                        return sinkProvider;
                    }

                    @Override
                    public MetadataApplier getMetadataApplier() {
                        return applier;
                    }
                };
            }
        };
        DataSink sink = sinkFactory.createDataSink(context);
        assertSame(sinkProvider, sink.getEventSinkProvider());
        assertSame(applier, sink.getMetadataApplier());
        assertSame(applier, applier.setAcceptedSchemaEvolutionTypes(
                EnumSet.of(SchemaChangeEventType.ADD_COLUMN)));
        assertTrue(applier.acceptsSchemaEvolutionType(SchemaChangeEventType.ADD_COLUMN));
        assertFalse(applier.getSupportedSchemaEvolutionTypes().isEmpty());
    }

    @Test
    void schemaEvolveExceptionsPreserveOriginalEventAndCause() {
        SchemaChangeEvent event = new SchemaChangeEvent() {
            @Override
            public TableId tableId() {
                return TableId.tableId("db", "orders");
            }

            @Override
            public SchemaChangeEventType getType() {
                return SchemaChangeEventType.ADD_COLUMN;
            }

            @Override
            public SchemaChangeEvent copy(TableId newTableId) {
                return this;
            }
        };
        IllegalStateException cause = new IllegalStateException("cause");
        SchemaEvolveException failure = new SchemaEvolveException(event, "schema", cause);
        assertSame(event, failure.getApplyingEvent());
        assertSame(cause, failure.getCause());
    }
}
