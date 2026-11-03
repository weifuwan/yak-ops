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
import io.yak.ops.cdc.common.exceptions.SchemaEvolveException;
import io.yak.ops.cdc.common.pipeline.SchemaChangeBehavior;
import io.yak.ops.cdc.common.sink.MetadataApplier;
import io.yak.ops.cdc.runtime.operators.OneInputStreamOperator;
import io.yak.ops.cdc.runtime.operators.Output;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Single-threaded SchemaOperator boundary for the regular Flink CDC pipeline.
 *
 * <p>Apply metadata changes synchronously before forwarding them; following DML cannot
 * overtake the schema change. This intentionally does not implement Flink's schema RPC,
 * multi-table routing, schema coercion or distributed coordinator state.
 */
public final class StandaloneSchemaOperator implements OneInputStreamOperator<Event, Event> {
    private static final Logger LOG = Logger.getLogger(StandaloneSchemaOperator.class.getName());

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
                if (isCreateTable || schemaChangeBehavior == SchemaChangeBehavior.EVOLVE) {
                    throw new UnsupportedOperationException(
                            "Sink does not accept schema evolution event: "
                                    + schemaEvent.getType());
                }
                // The target has not changed; do not send the rejected DDL to the writer.
                return;
            }
            try {
                metadataApplier.applySchemaChange(schemaEvent);
            } catch (SchemaEvolveException failure) {
                if (!isCreateTable
                        && (schemaChangeBehavior == SchemaChangeBehavior.TRY_EVOLVE
                                || schemaChangeBehavior == SchemaChangeBehavior.LENIENT)) {
                    // Best-effort schema evolution only. Full LENIENT row coercion is not ported.
                    LOG.log(Level.WARNING, "Schema change could not be applied: "
                            + schemaEvent, failure);
                    return;
                }
                throw failure;
            }
        }
        output.collect(event);
    }

    @Override
    public void close() throws Exception {
        metadataApplier.close();
    }
}
