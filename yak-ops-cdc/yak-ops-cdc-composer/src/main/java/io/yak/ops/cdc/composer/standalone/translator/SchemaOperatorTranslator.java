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

import io.yak.ops.cdc.common.event.Event;
import io.yak.ops.cdc.common.pipeline.SchemaChangeBehavior;
import io.yak.ops.cdc.common.sink.MetadataApplier;
import io.yak.ops.cdc.runtime.datastream.DataStream;
import io.yak.ops.cdc.runtime.operators.schema.StandaloneSchemaOperator;
import java.util.Objects;

/** Builds the regular (non-distributed) schema stage of a standalone CDC pipeline. */
public final class SchemaOperatorTranslator {

    public DataStream<Event> translateRegular(
            DataStream<Event> input,
            int parallelism,
            boolean isBatchMode,
            MetadataApplier metadataApplier,
            SchemaChangeBehavior schemaChangeBehavior,
            String schemaOperatorUid) {
        if (parallelism != 1) {
            throw new UnsupportedOperationException("Local SchemaOperator requires parallelism=1");
        }
        Objects.requireNonNull(metadataApplier, "metadataApplier");
        Objects.requireNonNull(schemaChangeBehavior, "schemaChangeBehavior");
        return input.transform(
                        isBatchMode ? "SchemaBatchOperator" : "SchemaOperator",
                        () -> new StandaloneSchemaOperator(metadataApplier, schemaChangeBehavior))
                .uid(schemaOperatorUid);
    }
}
