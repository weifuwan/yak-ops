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

package io.yak.ops.cdc.runtime.datastream;

import io.yak.ops.cdc.runtime.operators.OneInputStreamOperator;
import io.yak.ops.cdc.runtime.operators.OperatorFactory;
import io.yak.ops.cdc.runtime.operators.SinkOperator;
import io.yak.ops.cdc.runtime.transformations.Transformation;
import java.util.Objects;

/**
 * A typed reference to a logical transformation output, not an event container or graph node.
 * StreamGraph nodes are created only when the execution environment materializes its graph.
 */
public final class DataStream<T> {
    private final StreamExecutionEnvironment environment;
    private final Transformation<T> transformation;

    DataStream(StreamExecutionEnvironment environment, Transformation<T> transformation) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.transformation = Objects.requireNonNull(transformation, "transformation");
    }

    public int getId() {
        return transformation.getId();
    }

    public DataStream<T> uid(String uid) {
        environment.setUid(transformation, uid);
        return this;
    }

    public <R> DataStream<R> transform(
            String name, OperatorFactory<? extends OneInputStreamOperator<T, R>> factory) {
        return environment.addTransform(transformation, name, factory);
    }

    public void addSink(String name, OperatorFactory<? extends SinkOperator<T>> factory) {
        addSink(name, factory, null);
    }

    public void addSink(
            String name, OperatorFactory<? extends SinkOperator<T>> factory, String uid) {
        environment.addSink(transformation, name, factory, uid);
    }
}
