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

package io.yak.ops.cdc.runtime.transformations;

import io.yak.ops.cdc.runtime.graph.OperatorType;
import io.yak.ops.cdc.runtime.operators.OperatorFactory;
import io.yak.ops.cdc.runtime.operators.SourceOperator;
import java.util.List;

/** A logical stream source with no upstream transformation. */
public final class SourceTransformation<T> extends Transformation<T> {
    public SourceTransformation(
            int id, String name, OperatorFactory<? extends SourceOperator<T>> factory) {
        super(id, name, factory);
    }

    @Override
    public OperatorType getOperatorType() {
        return OperatorType.SOURCE;
    }

    @Override
    public List<Transformation<?>> getInputs() {
        return List.of();
    }
}
