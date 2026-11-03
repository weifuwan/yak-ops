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

package io.yak.ops.cdc.runtime.graph;

import io.yak.ops.cdc.runtime.operators.OperatorFactory;
import io.yak.ops.cdc.runtime.operators.StreamOperator;
import java.util.Objects;

/** Logical node. The operator factory is invoked only when a task starts. */
public record StreamNode(
        int id,
        String name,
        String uid,
        OperatorType type,
        OperatorFactory<? extends StreamOperator> operatorFactory) {

    public StreamNode {
        if (id < 1) {
            throw new IllegalArgumentException("Node id must be positive");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Node name must not be blank");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(operatorFactory, "operatorFactory");
        if (uid != null && uid.isBlank()) {
            throw new IllegalArgumentException("UID must not be blank");
        }
    }

    public StreamNode withUid(String newUid) {
        return new StreamNode(id, name, newUid, type, operatorFactory);
    }
}
