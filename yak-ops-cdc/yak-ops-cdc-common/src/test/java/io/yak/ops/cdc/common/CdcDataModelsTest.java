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

package io.yak.ops.cdc.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.common.data.GenericArrayData;
import io.yak.ops.cdc.common.data.GenericRecordData;
import io.yak.ops.cdc.common.data.RecordData;
import io.yak.ops.cdc.common.event.CreateTableEvent;
import io.yak.ops.cdc.common.event.DataChangeEvent;
import io.yak.ops.cdc.common.event.OperationType;
import io.yak.ops.cdc.common.event.TableId;
import io.yak.ops.cdc.common.schema.Schema;
import io.yak.ops.cdc.common.sink.DefaultDataChangeEventHashFunctionProvider;
import io.yak.ops.cdc.common.sink.TableIdHashFunctionProvider;
import io.yak.ops.cdc.common.types.DataTypes;
import io.yak.ops.cdc.common.types.variant.Variant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regressions for the upstream Flink CDC 3.7 event, record, and schema contracts. */
class CdcDataModelsTest {

    @Test
    void recordAndDataChangeEventsPreserveBeforeAfterAndOperation() {
        TableId table = TableId.tableId("shop", "orders");
        RecordData before = GenericRecordData.of(11L, 5L);
        RecordData after = GenericRecordData.of(11L, 6L);

        DataChangeEvent inserted = DataChangeEvent.insertEvent(table, after);
        assertEquals(OperationType.INSERT, inserted.op());
        assertNull(inserted.before());
        assertSame(after, inserted.after());
        assertSame(table, inserted.tableId());

        DataChangeEvent updated = DataChangeEvent.updateEvent(table, before, after);
        assertEquals(OperationType.UPDATE, updated.op());
        assertSame(before, updated.before());
        assertSame(after, updated.after());

        DataChangeEvent deleted = DataChangeEvent.deleteEvent(table, before);
        assertEquals(OperationType.DELETE, deleted.op());
        assertSame(before, deleted.before());
        assertNull(deleted.after());

        assertEquals(2, before.getArity());
        assertEquals(11L, before.getLong(0));
        assertEquals(5L, before.getLong(1));
        assertEquals(11L, RecordData.createFieldGetter(DataTypes.BIGINT(), 0).getFieldOrNull(before));
    }

    @Test
    void schemaTypesAndHashProvidersUseTheSameDataModel() {
        Schema schema =
                Schema.newBuilder()
                        .physicalColumn("id", DataTypes.BIGINT())
                        .physicalColumn("amount", DataTypes.BIGINT())
                        .primaryKey("id")
                        .build();
        TableId table = TableId.tableId("shop", "orders");
        DataChangeEvent first = DataChangeEvent.insertEvent(table, GenericRecordData.of(11L, 5L));
        DataChangeEvent second = DataChangeEvent.insertEvent(table, GenericRecordData.of(11L, 6L));

        assertEquals(2, schema.getColumnCount());
        assertEquals(List.of("id"), schema.primaryKeys());
        assertEquals(DataTypes.BIGINT(), schema.getColumn("id").orElseThrow().getType());
        assertEquals(schema, new CreateTableEvent(table, schema).getSchema());

        var hashFunction =
                new DefaultDataChangeEventHashFunctionProvider().getHashFunction(table, schema);
        assertEquals(hashFunction.hashcode(first), hashFunction.hashcode(second));
        assertEquals(
                new TableIdHashFunctionProvider().getHashFunction(table, schema).hashcode(first),
                new TableIdHashFunctionProvider().getHashFunction(table, schema).hashcode(second));
    }

    @Test
    void genericArrayAndVariantKeepUpstreamAccessors() {
        GenericArrayData numbers = new GenericArrayData(new int[] {3, 5});
        assertEquals(2, numbers.size());
        assertEquals(3, numbers.getInt(0));
        assertEquals(5, numbers.getInt(1));

        Variant value = Variant.newBuilder().of(42);
        assertTrue(value.isPrimitive());
        assertEquals(42, value.getInt());
    }
}
