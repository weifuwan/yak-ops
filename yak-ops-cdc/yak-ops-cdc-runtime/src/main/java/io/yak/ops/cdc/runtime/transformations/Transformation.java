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
import io.yak.ops.cdc.runtime.operators.StreamOperator;
import java.util.List;
import java.util.Objects;

/**
 * A logical transformation, independent of StreamNode, StreamEdge and running operators.
 *
 * <p>Like Flink's Transformation model, an input relationship is described first and
 * translated into a StreamGraph separately. The id identifies a transformation within a job.
 */
public abstract class Transformation<T> {
    private final int id;
    private final String name;
    private final OperatorFactory<? extends StreamOperator> operatorFactory;
    private String uid;

    protected Transformation(
            int id, String name, OperatorFactory<? extends StreamOperator> operatorFactory) {
        if (id < 1) {
            throw new IllegalArgumentException("Transformation id must be positive");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Transformation name must not be blank");
        }
        this.id = id;
        this.name = name;
        this.operatorFactory = Objects.requireNonNull(operatorFactory, "operatorFactory");
    }

    public final int getId() {
        return id;
    }

    public final String getName() {
        return name;
    }

    public final String getUid() {
        return uid;
    }

    public final void setUid(String uid) {
        if (uid != null && uid.isBlank()) {
            throw new IllegalArgumentException("Transformation UID must not be blank");
        }
        this.uid = uid;
    }

    public final OperatorFactory<? extends StreamOperator> getOperatorFactory() {
        return operatorFactory;
    }

    public abstract OperatorType getOperatorType();

    public abstract List<Transformation<?>> getInputs();
}
