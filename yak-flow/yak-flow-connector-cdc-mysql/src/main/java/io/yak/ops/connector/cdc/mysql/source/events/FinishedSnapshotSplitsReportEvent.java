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

package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlSourceEnumerator;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlSourceReader;
import io.yak.ops.core.api.connector.source.SourceEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@link SourceEvent} that {@link MySqlSourceReader} sends to {@link MySqlSourceEnumerator} to
 * notify the snapshot split has read finished with the consistent binlog position.
 */
public class FinishedSnapshotSplitsReportEvent implements SourceEvent {

    private static final long serialVersionUID = 1L;

    private final Map<String, DebeziumOffset> finishedOffsets;
    private final Map<String, byte[]> finishedSchemaHistories;

    public FinishedSnapshotSplitsReportEvent(Map<String, DebeziumOffset> finishedOffsets) {
        this(finishedOffsets, Map.of());
    }

    /**
     * Each completed Chunk offers Debezium history through its HIGH watermark.
     * The coordinator retains only the history for the earliest completed HIGH,
     * since that is the starting point of the deferred Binlog split.
     */
    public FinishedSnapshotSplitsReportEvent(
            Map<String, DebeziumOffset> finishedOffsets, Map<String, byte[]> finishedSchemaHistories) {
        this.finishedOffsets = Map.copyOf(finishedOffsets);
        Map<String, byte[]> copied = new LinkedHashMap<>();
        finishedSchemaHistories.forEach((id, history) -> {
            if (!finishedOffsets.containsKey(id)) {
                throw new IllegalArgumentException("Unknown finished split schema history: " + id);
            }
            copied.put(id, history.clone());
        });
        this.finishedSchemaHistories = Map.copyOf(copied);
    }

    public Map<String, DebeziumOffset> getFinishedOffsets() {
        return finishedOffsets;
    }

    public Map<String, byte[]> getFinishedSchemaHistories() {
        Map<String, byte[]> copied = new LinkedHashMap<>();
        finishedSchemaHistories.forEach((id, history) -> copied.put(id, history.clone()));
        return copied;
    }

    @Override
    public String toString() {
        return "FinishedSnapshotSplitsReportEvent{finishedSplits=" + finishedOffsets.keySet() + ", offsets=redacted}";
    }
}
