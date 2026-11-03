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
import io.yak.ops.cdc.runtime.datastream.DataStream;
import java.util.Objects;

/**
 * Preserves Flink CDC's regular pre/post partition stages in a single-partition job.
 *
 * <p>With one partition there is no routing or network shuffle: the identity operations
 * preserve CDC event identity and ordering. Multi-partition hashing is deliberately excluded.
 */
public final class PartitioningTranslator {

    public DataStream<Event> translateRegular(
            DataStream<Event> input,
            int upstreamParallelism,
            int downstreamParallelism,
            boolean isBatchMode,
            OperatorUidGenerator uidGenerator) {
        if (upstreamParallelism != 1 || downstreamParallelism != 1) {
            throw new UnsupportedOperationException(
                    "Standalone partitioning supports only one upstream and downstream partition");
        }
        Objects.requireNonNull(uidGenerator, "uidGenerator");
        DataStream<Event> prePartition = input.<Event>transform(
                        isBatchMode ? "BatchPrePartition" : "PrePartition",
                        () -> (event, output) -> output.collect(event))
                .uid(uidGenerator.generateUid("pre-partition"));
        return prePartition.<Event>transform(
                        isBatchMode ? "BatchPostPartition" : "PostPartition",
                        () -> (event, output) -> output.collect(event))
                .uid(uidGenerator.generateUid("post-partition"));
    }
}
