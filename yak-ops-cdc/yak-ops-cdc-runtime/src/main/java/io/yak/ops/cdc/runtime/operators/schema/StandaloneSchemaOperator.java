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

package io.yak.ops.cdc.runtime.operators.schema;

import io.yak.ops.cdc.common.event.Event;
import io.yak.ops.cdc.common.event.SchemaChangeEvent;
import io.yak.ops.cdc.common.event.SchemaChangeEventType;
import io.yak.ops.cdc.common.pipeline.SchemaChangeBehavior;
import io.yak.ops.cdc.common.sink.MetadataApplier;
import io.yak.ops.cdc.runtime.operators.OneInputStreamOperator;
import io.yak.ops.cdc.runtime.operators.Output;
import java.util.Objects;

/**
 * Single-threaded SchemaOperator boundary for the regular Flink CDC pipeline.
 *
 * <p>Apply metadata changes synchronously before forwarding them; following DML cannot
 * overtake the schema change. This intentionally does not implement Flink's schema RPC,
 * multi-table routing, schema coercion or distributed coordinator state.
 */
public final class StandaloneSchemaOperator implements OneInputStreamOperator<Event, Event> {
    private final MetadataApplier metadataApplier;
    private final SchemaChangeBehavior schemaChangeBehavior;

    public StandaloneSchemaOperator(
            MetadataApplier metadataApplier, SchemaChangeBehavior schemaChangeBehavior) {
        this.metadataApplier = Objects.requireNonNull(metadataApplier, "metadataApplier");
        this.schemaChangeBehavior =
                Objects.requireNonNull(schemaChangeBehavior, "schemaChangeBehavior");
    }

    @Override
    public void processElement(Event event, Output<Event> output) throws Exception {
        if (event instanceof SchemaChangeEvent schemaEvent) {
            boolean isCreateTable =
                    schemaEvent.getType() == SchemaChangeEventType.CREATE_TABLE;
            if (!isCreateTable && schemaChangeBehavior == SchemaChangeBehavior.IGNORE) {
                return;
            }
            if (!isCreateTable && schemaChangeBehavior == SchemaChangeBehavior.EXCEPTION) {
                throw new IllegalStateException(
                        "Schema change rejected by EXCEPTION behavior: " + schemaEvent);
            }
            if (!metadataApplier.acceptsSchemaEvolutionType(schemaEvent.getType())) {
                throw new UnsupportedOperationException(
                        "Sink does not accept schema evolution event: "
                                + schemaEvent.getType()
                                + "; standalone cannot safely coerce following DML");
            }
            // Unlike Flink's full LENIENT implementation, there is no row-schema coercion
            // yet. Do not continue with DML after a failed DDL: propagate the failure.
            metadataApplier.applySchemaChange(schemaEvent);
        }
        output.collect(event);
    }

    @Override
    public void close() throws Exception {
        metadataApplier.close();
    }
}
