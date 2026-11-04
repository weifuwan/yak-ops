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
import org.apache.flink.cdc.common.types.DataTypes;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises multi-table writer ownership without opening a database connection. */
class JdbcTableWriterRegistryTest {
    private static final Schema SCHEMA =
            Schema.newBuilder().physicalColumn("id", DataTypes.BIGINT()).build();
    private static final TableId A = TableId.tableId("db", "a");
    private static final TableId B = TableId.tableId("db", "b");
    private static final TableId C = TableId.tableId("db", "c");

    @Test
    void reusesOpenTablesAndEvictsTheLeastRecentlyUsedAfterFlushing() throws Exception {
        List<String> operations = new ArrayList<>();
        JdbcTableWriterRegistry<FakeWriter> registry =
                registry(2, operations);
        FakeWriter first = registry.getOrCreate(A, SCHEMA);
        FakeWriter second = registry.getOrCreate(B, SCHEMA);
        assertSame(first, registry.getOrCreate(A, SCHEMA));

        registry.getOrCreate(C, SCHEMA);

        assertEquals(List.of("flush:b", "close:b"), operations);
        assertEquals(2, registry.size());
        assertEquals(0, first.closes);
        assertEquals(1, second.closes);
        assertNotSame(second, registry.getOrCreate(B, SCHEMA));
        assertEquals(2, registry.size());
        registry.close();
    }

    @Test
    void aFailedEvictionFlushNeverDiscardsBufferedWriter() throws Exception {
        List<String> operations = new ArrayList<>();
        JdbcTableWriterRegistry<FakeWriter> registry = registry(1, operations);
        FakeWriter a = registry.getOrCreate(A, SCHEMA);
        a.failFlush = true;

        assertThrows(IOException.class, () -> registry.getOrCreate(B, SCHEMA));
        assertEquals(1, registry.size());
        assertSame(a, registry.getOrCreate(A, SCHEMA));
        assertEquals(0, a.closes);
        a.failFlush = false;
        registry.close();
    }

    @Test
    void aFailedCloseDoesNotPreventClosingOtherTables() throws Exception {
        List<String> operations = new ArrayList<>();
        JdbcTableWriterRegistry<FakeWriter> registry = registry(3, operations);
        FakeWriter a = registry.getOrCreate(A, SCHEMA);
        FakeWriter b = registry.getOrCreate(B, SCHEMA);
        FakeWriter c = registry.getOrCreate(C, SCHEMA);
        a.failClose = true;
        b.failClose = true;

        IOException failure = assertThrows(IOException.class, registry::close);
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(1, c.closes);
        assertEquals(0, registry.size());
        assertEquals(List.of("close:a", "close:b", "close:c"), operations);
    }

    @Test
    void flushAllAttemptsOtherTablesWhenOneFlushFails() throws Exception {
        List<String> operations = new ArrayList<>();
        JdbcTableWriterRegistry<FakeWriter> registry = registry(2, operations);
        FakeWriter a = registry.getOrCreate(A, SCHEMA);
        registry.getOrCreate(B, SCHEMA);
        a.failFlush = true;
        assertThrows(IOException.class, registry::flushAll);
        assertEquals(List.of("flush:a", "flush:b"), operations);
        a.failFlush = false;
        registry.close();
    }

    @Test
    void schemaChangeInvalidatesOnlyOneTable() throws Exception {
        List<String> operations = new ArrayList<>();
        JdbcTableWriterRegistry<FakeWriter> registry = registry(2, operations);
        FakeWriter a = registry.getOrCreate(A, SCHEMA);
        FakeWriter b = registry.getOrCreate(B, SCHEMA);

        registry.flushAll();
        registry.invalidate(A);

        assertEquals(1, a.closes);
        assertEquals(0, b.closes);
        assertSame(b, registry.getOrCreate(B, SCHEMA));
        assertNotSame(a, registry.getOrCreate(A, SCHEMA));
        registry.close();
    }

    @Test
    void rejectsInvalidCapacityAndRetainsFailedCloseForEviction() throws Exception {
        List<String> operations = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () -> registry(0, operations));

        JdbcTableWriterRegistry<FakeWriter> registry = registry(1, operations);
        FakeWriter a = registry.getOrCreate(A, SCHEMA);
        a.failClose = true;
        assertThrows(IOException.class, () -> registry.getOrCreate(B, SCHEMA));
        assertEquals(1, registry.size());
        assertEquals(1, a.closes);
        a.failClose = false;
        registry.close();
        assertTrue(operations.contains("close:a"));
    }

    private static JdbcTableWriterRegistry<FakeWriter> registry(
            int maxOpenTables, List<String> operations) {
        return new JdbcTableWriterRegistry<>(
                maxOpenTables,
                (tableId, schema) -> new FakeWriter(tableId.getTableName(), operations),
                FakeWriter::flush);
    }

    private static final class FakeWriter implements AutoCloseable {
        final String name;
        final List<String> operations;
        int closes;
        boolean failFlush;
        boolean failClose;

        FakeWriter(String name, List<String> operations) {
            this.name = name;
            this.operations = operations;
        }

        void flush() throws IOException {
            operations.add("flush:" + name);
            if (failFlush) {
                throw new IOException("flush failed: " + name);
            }
        }

        @Override
        public void close() throws IOException {
            operations.add("close:" + name);
            closes++;
            if (failClose) {
                throw new IOException("close failed: " + name);
            }
        }
    }
}
