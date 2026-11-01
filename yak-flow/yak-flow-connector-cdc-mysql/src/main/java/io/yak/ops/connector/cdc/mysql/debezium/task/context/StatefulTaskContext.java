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

package io.yak.ops.connector.cdc.mysql.debezium.task.context;

import io.yak.ops.connector.cdc.mysql.debezium.BinlogEvent;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlDebeziumRecordConverter;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the mutable Debezium reader attempt state independently of the source split.
 *
 * <p>Only the last mailbox-emitted offset is checkpointed. A prefetched, queued offset is
 * never considered durable progress. The state is scoped to one reader attempt.
 *
 * <p>Unlike Flink CDC's 1.9-based StatefulTaskContext, Debezium 2.7's embedded
 * engine still owns its connector internals until native ChangeEventSource integration.
 */
public final class StatefulTaskContext {

    private final MySqlSourceConfig sourceConfig;
    private final int serverId;
    private final MySqlDebeziumRecordConverter converter;
    private final BlockingQueue<BinlogEvent> queue;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private MySqlBinlogSplit split;

    public StatefulTaskContext(MySqlSourceConfig sourceConfig, int serverId) {
        this.sourceConfig = Objects.requireNonNull(sourceConfig, "sourceConfig");
        if (serverId <= 0) {
            throw new IllegalArgumentException("Replication server ID must be positive");
        }
        this.serverId = serverId;
        this.converter = new MySqlDebeziumRecordConverter(
                sourceConfig.schemas(),
                sourceConfig.physicalSchemas(),
                sourceConfig.schemaEvolutionEnabled(),
                sourceConfig.isScanNewlyAddedTableEnabled());
        this.queue = new ArrayBlockingQueue<>(sourceConfig.queueCapacity());
    }

    public void configure(MySqlBinlogSplit split) {
        if (this.split != null) {
            throw new IllegalStateException("Debezium reader context cannot be configured twice");
        }
        this.split = Objects.requireNonNull(split, "split");
        if (split.offset() != null && split.schemaHistory().length == 0) {
            throw new IllegalArgumentException("A restored MySQL Binlog offset requires Schema History");
        }
    }

    public MySqlSourceConfig getSourceConfig() {
        return sourceConfig;
    }

    public int getServerId() {
        return serverId;
    }

    public MySqlDebeziumRecordConverter converter() {
        return converter;
    }

    public DebeziumOffset restoredOffset() {
        return requireSplit().offset();
    }

    public byte[] restoredSchemaHistory() {
        return requireSplit().schemaHistory();
    }

    private MySqlBinlogSplit requireSplit() {
        return Objects.requireNonNull(split, "Debezium task context is not configured");
    }

    public boolean offer(BinlogEvent event) throws InterruptedException {
        return queue.offer(Objects.requireNonNull(event, "event"), 250, TimeUnit.MILLISECONDS);
    }

    public BinlogEvent poll() throws InterruptedException {
        checkFailure();
        BinlogEvent next = queue.poll(250, TimeUnit.MILLISECONDS);
        checkFailure();
        return next;
    }

    public void recordFailure(Throwable error) {
        failure.compareAndSet(null, Objects.requireNonNull(error, "error"));
    }

    private void checkFailure() {
        Throwable error = failure.get();
        if (error != null) {
            throw new IllegalStateException("MySQL Binlog Debezium engine failed", error);
        }
    }
}
