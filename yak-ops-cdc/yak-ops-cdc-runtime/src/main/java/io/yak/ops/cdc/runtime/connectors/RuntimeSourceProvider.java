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

package io.yak.ops.cdc.runtime.connectors;

import io.yak.ops.cdc.common.event.Event;
import io.yak.ops.cdc.common.source.EventSourceProvider;
import io.yak.ops.cdc.runtime.operators.SourceOperator;

/**
 * Marker bridge from a Flink CDC DataSource to the standalone runtime SourceOperator.
 *
 * <p>Production connectors must implement this bridge when standalone reader adaptation
 * becomes available. Merely implementing EventSourceProvider is not sufficient to execute.
 */
public interface RuntimeSourceProvider extends EventSourceProvider {
    SourceOperator<Event> createSourceOperator() throws Exception;
}
