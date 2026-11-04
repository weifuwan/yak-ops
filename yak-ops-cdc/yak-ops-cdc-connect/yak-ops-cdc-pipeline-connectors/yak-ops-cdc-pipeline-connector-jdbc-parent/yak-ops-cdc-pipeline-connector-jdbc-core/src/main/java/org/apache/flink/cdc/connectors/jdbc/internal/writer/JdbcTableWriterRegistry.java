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

package org.apache.flink.cdc.connectors.jdbc.internal.writer;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Bounded, access-ordered lifecycle manager for physical JDBC table writers.
 *
 * <p>Eviction first flushes and closes the least recently used writer. A failed flush or close
 * propagates to the Sink, instead of silently discarding buffered CDC rows. Close always
 * attempts every writer, preserving additional failures as suppressed exceptions.
 */
public final class JdbcTableWriterRegistry<T extends AutoCloseable> implements AutoCloseable {

    @FunctionalInterface
    public interface Factory<T> {
        T create(TableId tableId, Schema schema) throws IOException;
    }

    @FunctionalInterface
    public interface Flusher<T> {
        void flush(T writer) throws IOException;
    }

    private final int maxOpenTables;
    private final Factory<T> factory;
    private final Flusher<T> flusher;
    private final Map<TableId, T> writers = new LinkedHashMap<>(16, 0.75f, true);

    public JdbcTableWriterRegistry(int maxOpenTables, Factory<T> factory, Flusher<T> flusher) {
        checkArgument(maxOpenTables > 0, "maxOpenTables must be positive");
        this.maxOpenTables = maxOpenTables;
        this.factory = checkNotNull(factory);
        this.flusher = checkNotNull(flusher);
    }

    public T getOrCreate(TableId tableId, Schema schema) throws IOException {
        T existing = writers.get(tableId);
        if (existing != null) {
            return existing;
        }
        if (writers.size() >= maxOpenTables) {
            Map.Entry<TableId, T> oldest = writers.entrySet().iterator().next();
            // Flush + close must finish before removing a writer from the registry.
            flusher.flush(oldest.getValue());
            closeOne(oldest.getValue());
            writers.remove(oldest.getKey());
        }
        T created = factory.create(tableId, schema);
        writers.put(tableId, checkNotNull(created, "Table writer factory returned null"));
        return created;
    }

    public void invalidate(TableId tableId) throws IOException {
        T writer = writers.get(tableId);
        if (writer != null) {
            closeOne(writer);
            writers.remove(tableId);
        }
    }

    public void flushAll() throws IOException {
        IOException failure = null;
        // Snapshot protects iteration against re-entrant close/flush callbacks.
        for (T writer : new ArrayList<>(writers.values())) {
            try {
                flusher.flush(writer);
            } catch (Exception e) {
                failure = collect(failure, "Failed to flush JDBC table writers", e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public int size() {
        return writers.size();
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        try {
            for (T writer : new ArrayList<>(writers.values())) {
                try {
                    closeOne(writer);
                } catch (Exception e) {
                    failure = collect(failure, "Failed to close JDBC table writers", e);
                }
            }
        } finally {
            writers.clear();
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void closeOne(AutoCloseable writer) throws IOException {
        try {
            writer.close();
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed to close JDBC table writer", e);
        }
    }

    private static IOException collect(IOException existing, String message, Exception error) {
        IOException failure =
                error instanceof IOException ? (IOException) error : new IOException(message, error);
        if (existing == null) {
            return failure;
        }
        if (existing != failure) {
            existing.addSuppressed(failure);
        }
        return existing;
    }
}
