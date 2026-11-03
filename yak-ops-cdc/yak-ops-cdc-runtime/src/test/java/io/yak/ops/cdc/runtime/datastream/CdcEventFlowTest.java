/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.common.data.GenericRecordData;
import io.yak.ops.cdc.common.data.binary.BinaryRecordData;
import io.yak.ops.cdc.common.data.binary.BinaryStringData;
import io.yak.ops.cdc.common.event.AddColumnEvent;
import io.yak.ops.cdc.common.event.CreateTableEvent;
import io.yak.ops.cdc.common.event.DataChangeEvent;
import io.yak.ops.cdc.common.event.Event;
import io.yak.ops.cdc.common.event.OperationType;
import io.yak.ops.cdc.common.event.TableId;
import io.yak.ops.cdc.common.schema.Column;
import io.yak.ops.cdc.common.schema.Schema;
import io.yak.ops.cdc.common.types.DataTypes;
import io.yak.ops.cdc.common.types.RowType;
import io.yak.ops.cdc.runtime.graph.StreamEdge;
import io.yak.ops.cdc.runtime.graph.StreamGraph;
import io.yak.ops.cdc.runtime.typeutils.BinaryRecordDataGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Verifies real CDC Event subclasses across the logical graph and local operator chain. */
class CdcEventFlowTest {

    @Test
    void schemaAndDmlStayOrderedAcrossTwoTransformStages() throws Exception {
        TableId tableId = TableId.tableId("cdc_lab", "orders");
        Schema schema = Schema.newBuilder()
                .physicalColumn("id", DataTypes.BIGINT())
                .physicalColumn("name", DataTypes.STRING())
                .primaryKey("id").build();
        CreateTableEvent create = new CreateTableEvent(tableId, schema);
        DataChangeEvent insert = DataChangeEvent.insertEvent(
                tableId, row(1L, "before"), Map.of("binlog-file", "mysql-bin.000001"));
        DataChangeEvent update = DataChangeEvent.updateEvent(
                tableId, row(1L, "before"), row(1L, "after"), Map.of("binlog-pos", "42"));
        DataChangeEvent delete = DataChangeEvent.deleteEvent(tableId, row(1L, "after"));
        AddColumnEvent addColumn = new AddColumnEvent(tableId, List.of(
                AddColumnEvent.last(Column.physicalColumn("remark", DataTypes.STRING()))));
        DataChangeEvent afterAlter = DataChangeEvent.insertEvent(
                tableId, GenericRecordData.of(2L, BinaryStringData.fromString("new"),
                        BinaryStringData.fromString("remark")));
        List<Event> sent = List.of(create, insert, update, delete, addColumn, afterAlter);
        List<Event> seenAtFirst = new ArrayList<>();
        List<Event> seenAtSecond = new ArrayList<>();
        List<Event> received = new ArrayList<>();
        AtomicInteger instantiated = new AtomicInteger();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        DataStream<Event> source = env.<Event>fromSource("source", () -> {
            instantiated.incrementAndGet();
            return output -> {
                for (Event event : sent) {
                    output.collect(event);
                }
            };
        }).uid("source-uid");
        DataStream<Event> first = source.<Event>transform("schema-stage", () -> {
            instantiated.incrementAndGet();
            return (event, output) -> {
                seenAtFirst.add(event);
                output.collect(event);
            };
        }).uid("schema-uid");
        DataStream<Event> second = first.<Event>transform("data-stage", () -> {
            instantiated.incrementAndGet();
            return (event, output) -> {
                seenAtSecond.add(event);
                output.collect(event);
            };
        }).uid("data-uid");
        second.addSink("sink", () -> {
            instantiated.incrementAndGet();
            return received::add;
        }, "sink-uid");

        StreamGraph graph = env.getStreamGraph();
        assertEquals(0, instantiated.get());
        assertEquals(List.of(1, 2, 3, 4),
                graph.getOrderedNodes().stream().map(n -> n.getId()).toList());
        assertEquals(List.of(1, 2, 3),
                graph.getEdges().stream().map(StreamEdge::getSourceId).toList());
        assertEquals(List.of(2, 3, 4),
                graph.getEdges().stream().map(StreamEdge::getTargetId).toList());
        assertEquals("schema-uid", graph.getStreamNode(2).getTransformationUID());

        env.execute("cdc-events");

        assertEquals(4, instantiated.get());
        assertEquals(sent, seenAtFirst);
        assertEquals(sent, seenAtSecond);
        assertEquals(sent, received);
        for (int i = 0; i < sent.size(); i++) {
            assertSame(sent.get(i), seenAtFirst.get(i));
            assertSame(sent.get(i), seenAtSecond.get(i));
            assertSame(sent.get(i), received.get(i));
        }
        assertSame(schema, ((CreateTableEvent) received.get(0)).getSchema());
        assertEquals(OperationType.INSERT, ((DataChangeEvent) received.get(1)).op());
        assertEquals(OperationType.UPDATE, ((DataChangeEvent) received.get(2)).op());
        assertEquals(OperationType.DELETE, ((DataChangeEvent) received.get(3)).op());
        assertEquals("mysql-bin.000001",
                ((DataChangeEvent) received.get(1)).meta().get("binlog-file"));
        assertEquals("42", ((DataChangeEvent) received.get(2)).meta().get("binlog-pos"));
        assertEquals("before",
                ((DataChangeEvent) received.get(2)).before().getString(1).toString());
        assertEquals("after",
                ((DataChangeEvent) received.get(2)).after().getString(1).toString());
        assertEquals(tableId, ((AddColumnEvent) received.get(4)).tableId());
        assertEquals(3, ((DataChangeEvent) received.get(5)).after().getArity());
    }

    @Test
    void directSourceToSinkPreservesSchemaFirst() throws Exception {
        TableId tableId = TableId.tableId("cdc_lab", "orders");
        CreateTableEvent create = new CreateTableEvent(tableId,
                Schema.newBuilder().physicalColumn("id", DataTypes.BIGINT()).build());
        DataChangeEvent insert = DataChangeEvent.insertEvent(tableId, GenericRecordData.of(7L));
        List<Event> received = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        env.<Event>fromSource("source", () -> output -> {
            output.collect(create);
            output.collect(insert);
        }).addSink("sink", () -> received::add);
        env.execute("direct");
        assertEquals(2, received.size());
        assertSame(create, received.get(0));
        assertSame(insert, received.get(1));
    }

    @Test
    void failedTransformDoesNotSilentlyContinueDeliveringChanges() {
        TableId tableId = TableId.tableId("cdc_lab", "orders");
        CreateTableEvent create = new CreateTableEvent(tableId,
                Schema.newBuilder().physicalColumn("id", DataTypes.BIGINT()).build());
        DataChangeEvent insert = DataChangeEvent.insertEvent(tableId, GenericRecordData.of(1L));
        DataChangeEvent later = DataChangeEvent.insertEvent(tableId, GenericRecordData.of(2L));
        List<Event> received = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        env.<Event>fromSource("source", () -> output -> {
                    output.collect(create);
                    output.collect(insert);
                    output.collect(later);
                })
                .<Event>transform("fail", () -> (event, output) -> {
                    if (event instanceof DataChangeEvent) {
                        throw new IllegalStateException("invalid row");
                    }
                    output.collect(event);
                })
                .addSink("sink", () -> received::add);
        IllegalStateException error = assertThrows(
                IllegalStateException.class, () -> env.execute("fail"));
        assertEquals("invalid row", error.getMessage());
        assertEquals(1, received.size());
        assertSame(create, received.get(0));
    }

    @Test
    void binaryRecordDataPayloadReachesSinkWithoutConversionOrMemoryReuse() throws Exception {
        TableId tableId = TableId.tableId("cdc_lab", "orders");
        RowType rowType = DataTypes.ROW(
                DataTypes.FIELD("id", DataTypes.BIGINT()),
                DataTypes.FIELD("name", DataTypes.STRING()));
        BinaryRecordDataGenerator generator = new BinaryRecordDataGenerator(rowType);
        BinaryRecordData first = generator.generate(new Object[] {
                1L, BinaryStringData.fromString("first")
        });
        BinaryRecordData second = generator.generate(new Object[] {
                2L, BinaryStringData.fromString("second")
        });
        DataChangeEvent firstInsert = DataChangeEvent.insertEvent(tableId, first);
        DataChangeEvent secondInsert = DataChangeEvent.insertEvent(tableId, second);
        CreateTableEvent create = new CreateTableEvent(tableId,
                Schema.newBuilder()
                        .physicalColumn("id", DataTypes.BIGINT())
                        .physicalColumn("name", DataTypes.STRING()).build());
        List<Event> received = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getLocalEnvironment();
        env.<Event>fromSource("source", () -> output -> {
                    output.collect(create);
                    output.collect(firstInsert);
                    output.collect(secondInsert);
                })
                .<Event>transform("identity", () -> (event, output) -> output.collect(event))
                .addSink("sink", () -> received::add);
        env.execute("binary-record-events");

        assertEquals(3, received.size());
        assertSame(create, received.get(0));
        assertSame(firstInsert, received.get(1));
        assertSame(secondInsert, received.get(2));
        assertSame(first, ((DataChangeEvent) received.get(1)).after());
        assertSame(second, ((DataChangeEvent) received.get(2)).after());
        assertEquals(1L, ((DataChangeEvent) received.get(1)).after().getLong(0));
        assertEquals("first",
                ((DataChangeEvent) received.get(1)).after().getString(1).toString());
        assertEquals(2L, ((DataChangeEvent) received.get(2)).after().getLong(0));
        assertEquals("second",
                ((DataChangeEvent) received.get(2)).after().getString(1).toString());
    }

    private static GenericRecordData row(long id, String name) {
        return GenericRecordData.of(id, BinaryStringData.fromString(name));
    }
}
