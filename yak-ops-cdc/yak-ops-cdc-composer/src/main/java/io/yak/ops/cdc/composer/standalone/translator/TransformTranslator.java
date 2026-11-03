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
import io.yak.ops.cdc.composer.definition.TransformDef;
import io.yak.ops.cdc.runtime.datastream.DataStream;
import io.yak.ops.cdc.runtime.operators.OneInputStreamOperator;
import java.util.List;
import java.util.Objects;

/**
 * Preserve Flink CDC 3.7 Pre/PostTransform topology while expression evaluation is not ported.
 *
 * <p>All matching TransformDef rules belong to each stage, not a chain of independently applied
 * rules. An unported stage fails on execution instead of silently passing through data.
 */
public class TransformTranslator {

    public DataStream<Event> translatePreTransform(
            DataStream<Event> input, List<TransformDef> rules) {
        Objects.requireNonNull(rules, "rules");
        if (rules.isEmpty()) {
            return input;
        }
        return input.transform("Transform:Schema",
                () -> unsupportedOperator("PreTransform", rules));
    }

    public DataStream<Event> translatePostTransform(
            DataStream<Event> input, List<TransformDef> rules,
            OperatorUidGenerator uidGenerator) {
        Objects.requireNonNull(rules, "rules");
        if (rules.isEmpty()) {
            return input;
        }
        return input.transform("Transform:Data",
                () -> unsupportedOperator("PostTransform", rules))
                .uid(uidGenerator.generateUid("post-transform"));
    }

    private static OneInputStreamOperator<Event, Event> unsupportedOperator(
            String stage, List<TransformDef> rules) {
        List<TransformDef> definitions = List.copyOf(rules);
        return (event, output) -> {
            throw new UnsupportedOperationException(
                    stage + " expression execution is not yet implemented for "
                            + definitions.size() + " transform rule(s)");
        };
    }
}
