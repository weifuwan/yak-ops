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

package io.yak.ops.cdc.common.utils;

import io.yak.ops.cdc.common.annotation.VisibleForTesting;
import io.yak.ops.cdc.common.event.SchemaChangeEventType;
import io.yak.ops.cdc.common.event.SchemaChangeEventTypeFamily;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Utilities for handling schema change events. */
public class ChangeEventUtils {
    public static Set<SchemaChangeEventType> resolveSchemaEvolutionOptions(
            List<String> includedSchemaEvolutionTypes, List<String> excludedSchemaEvolutionTypes) {
        List<SchemaChangeEventType> resultTypes = new ArrayList<>();

        for (String includeTag : includedSchemaEvolutionTypes) {
            resultTypes.addAll(resolveSchemaEvolutionTag(includeTag));
        }

        for (String excludeTag : excludedSchemaEvolutionTypes) {
            resultTypes.removeAll(resolveSchemaEvolutionTag(excludeTag));
        }

        return new HashSet<>(resultTypes);
    }

    @VisibleForTesting
    public static List<SchemaChangeEventType> resolveSchemaEvolutionTag(String tag) {
        List<SchemaChangeEventType> types =
                new ArrayList<>(Arrays.asList(SchemaChangeEventTypeFamily.ofTag(tag)));
        if (types.isEmpty()) {
            // It's a specified tag
            SchemaChangeEventType type = SchemaChangeEventType.ofTag(tag);
            if (type != null) {
                types.add(type);
            }
        }
        return types;
    }
}
