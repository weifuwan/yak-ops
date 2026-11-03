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

package io.yak.ops.cdc.runtime.datastream;

import io.yak.ops.cdc.runtime.operators.OneInputStreamOperator;
import io.yak.ops.cdc.runtime.operators.OperatorFactory;
import io.yak.ops.cdc.runtime.operators.SinkOperator;
import java.util.Objects;

/**
 * A reference to a logical output, not an event container. Each transform returns a new reference.
 */
public final class DataStream<T> {
    private final StreamExecutionEnvironment environment;
    private final int nodeId;

    DataStream(StreamExecutionEnvironment environment, int nodeId) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.nodeId = nodeId;
    }

    public int getId() {
        return nodeId;
    }

    public DataStream<T> uid(String uid) {
        environment.setUid(nodeId, uid);
        return this;
    }

    public <R> DataStream<R> transform(
            String name, OperatorFactory<? extends OneInputStreamOperator<T, R>> factory) {
        return environment.addTransform(nodeId, name, factory);
    }

    public void addSink(String name, OperatorFactory<? extends SinkOperator<T>> factory) {
        addSink(name, factory, null);
    }

    public void addSink(
            String name, OperatorFactory<? extends SinkOperator<T>> factory, String uid) {
        environment.addSink(nodeId, name, factory, uid);
    }
}
