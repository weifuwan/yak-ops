package io.yak.ops.flow.runtime.partitioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.AddColumnEvent;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.function.HashFunction;
import io.yak.ops.core.function.HashFunctionProvider;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.sink.DefaultDataChangeEventHashFunctionProvider;
import io.yak.ops.core.types.BasicType;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.VarBinaryType;
import io.yak.ops.core.types.VarCharType;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.graph.StreamEdge;
import io.yak.ops.flow.runtime.graph.StreamPartitioning;
import io.yak.ops.flow.runtime.io.RecordWriterOutput;
import io.yak.ops.flow.runtime.io.partition.InputGate;
import io.yak.ops.flow.runtime.io.partition.ResultPartition;
import io.yak.ops.flow.runtime.io.partitioner.EventPartitioner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventPartitioningTest {

    private static final TableId TABLE = new TableId("inventory", null, "products");
    private static final Schema SCHEMA = new Schema(
            List.of(new PhysicalColumn("id", new BasicType(false, LogicalTypeRoot.INTEGER))), List.of("id"));

    @Test
    void schemaBroadcastPrecedesDataAndCheckpointInEveryTargetChannel() throws Exception {
        List<InputGate<PartitioningEvent>> inputs =
                List.of(new InputGate<>(8, 1), new InputGate<>(8, 1), new InputGate<>(8, 1));
        RecordWriterOutput<PartitioningEvent> network = new RecordWriterOutput<>(
                new StreamEdge(1, 2, StreamPartitioning.EVENT), 0, 1, new ResultPartition<>(0, inputs));
        RegularPrePartitionOperator operator = new RegularPrePartitionOperator(3, (table, schema) -> event -> -1);
        operator.setup(taskInfo(0, 1));
        CreateTableEvent create = new CreateTableEvent(TABLE, SCHEMA);
        operator.processElement(create, network::collect);
        operator.processElement(DataChangeEvent.insertEvent(TABLE, GenericRowData.of(7)), network::collect);
        network.broadcastBarrier(42);
        network.finish();

        for (int target = 0; target < inputs.size(); target++) {
            List<Event> events = new ArrayList<>();
            List<Long> barriers = new ArrayList<>();
            PostPartitionProcessor post = new PostPartitionProcessor();
            int expectedEvents = target == 2 ? 2 : 1;
            InputGate.BarrierHandler handler = checkpointId -> {
                assertEquals(expectedEvents, events.size(), "The barrier must follow the target's schema and data");
                barriers.add(checkpointId);
            };
            while (inputs.get(target).emitNext(value -> post.processElement(value, events::add), handler)
                    != InputStatus.END_OF_INPUT) {}
            assertInstanceOf(CreateTableEvent.class, events.getFirst());
            assertNotSame(create, events.getFirst());
            assertEquals(target == 2 ? 2 : 1, events.size());
            if (target == 2) {
                assertInstanceOf(DataChangeEvent.class, events.getLast());
            }
            assertEquals(List.of(42L), barriers);
        }
    }

    @Test
    void primaryKeyAffinitySurvivesColumnPositionChangesAndDeleteImages() throws Exception {
        RegularPrePartitionOperator operator =
                new RegularPrePartitionOperator(7, new DefaultDataChangeEventHashFunctionProvider());
        operator.setup(taskInfo(0, 1));
        List<PartitioningEvent> events = new ArrayList<>();
        operator.processElement(new CreateTableEvent(TABLE, SCHEMA), events::add);
        operator.processElement(DataChangeEvent.insertEvent(TABLE, GenericRowData.of(11)), events::add);
        int target = events.getLast().getTargetPartition();
        operator.processElement(
                new AddColumnEvent(TABLE, List.of(AddColumnEvent.first(new PhysicalColumn("name", new VarCharType(64))))),
                events::add);
        operator.processElement(DataChangeEvent.deleteEvent(TABLE, GenericRowData.of("previous", 11)), events::add);
        assertEquals(target, events.getLast().getTargetPartition());
        operator.processElement(DataChangeEvent.updateEvent(
                TABLE, GenericRowData.of("previous", 11), GenericRowData.of("current", 11)), events::add);
        assertEquals(target, events.getLast().getTargetPartition());
        assertEquals(17, events.size());
    }

    @Test
    void binaryPrimaryKeysHashByValueAndRejectWrongTables() {
        Schema schema = new Schema(List.of(new PhysicalColumn("id", new VarBinaryType(32))), List.of("id"));
        HashFunction<DataChangeEvent> hash =
                new DefaultDataChangeEventHashFunctionProvider().getHashFunction(TABLE, schema);
        int first = hash.hashcode(DataChangeEvent.insertEvent(TABLE, GenericRowData.of(new byte[] {1, 2, 3})));
        int second = hash.hashcode(DataChangeEvent.deleteEvent(TABLE, GenericRowData.of(new byte[] {1, 2, 3})));
        assertEquals(first, second);
        assertTrue(first >= 0);
        assertThrows(IllegalArgumentException.class, () -> hash.hashcode(DataChangeEvent.insertEvent(
                new TableId("other", null, "products"), GenericRowData.of(new byte[] {1, 2, 3}))));
    }

    @Test
    void rejectsCrossPartitionAtomicUpdatesBeforeEmittingEitherImage() throws Exception {
        HashFunctionProvider<DataChangeEvent> beforeImageHash = (table, schema) -> event ->
                (event.before() == null ? event.after() : event.before()).getInt(0);
        for (HashFunctionProvider<DataChangeEvent> provider :
                List.of(new DefaultDataChangeEventHashFunctionProvider(), beforeImageHash)) {
            RegularPrePartitionOperator operator = new RegularPrePartitionOperator(2, provider);
            operator.setup(taskInfo(0, 1));
            List<PartitioningEvent> events = new ArrayList<>();
            operator.processElement(new CreateTableEvent(TABLE, SCHEMA), events::add);
            events.clear();
            DataChangeEvent update = DataChangeEvent.updateEvent(
                    TABLE, GenericRowData.of(1), GenericRowData.of(2), Map.of("origin", "native-source"))
                    .withChangeEventId(new ChangeEventId("source", "update-1", 0, 2));
            assertThrows(UnsupportedOperationException.class, () -> operator.processElement(update, events::add));
            assertTrue(events.isEmpty(), "A rejected UPDATE must emit neither image");
        }
    }

    @Test
    void updatesWithinOneDestinationRetainTheirAtomicEventAndCheckpointIdentity() throws Exception {
        for (int parallelism : List.of(1, 2)) {
            RegularPrePartitionOperator operator = new RegularPrePartitionOperator(
                    parallelism, new DefaultDataChangeEventHashFunctionProvider());
            operator.setup(taskInfo(0, 1));
            List<PartitioningEvent> events = new ArrayList<>();
            operator.processElement(new CreateTableEvent(TABLE, SCHEMA), events::add);
            events.clear();
            ChangeEventId identity = new ChangeEventId("source", "update-2", 0, 2);
            DataChangeEvent update = DataChangeEvent.updateEvent(
                    TABLE, GenericRowData.of(1), GenericRowData.of(3), Map.of("origin", "native-source"))
                    .withChangeEventId(identity);
            operator.processElement(update, events::add);
            assertEquals(1, events.size());
            assertSame(update, events.getFirst().getPayload());
            assertEquals(identity, ((DataChangeEvent) events.getFirst().getPayload()).changeEventId());
        }
    }

    @Test
    void customSinkHashReceivesTheOwningSubtaskAndDownstreamParallelism() throws Exception {
        RegularPrePartitionOperator operator = new RegularPrePartitionOperator(5, (table, schema) -> new HashFunction<>() {
            @Override
            public int hashcode(DataChangeEvent event) {
                throw new AssertionError("Context-aware hashing is required");
            }

            @Override
            public int hashcode(HashContext context, DataChangeEvent event) {
                assertEquals(3, context.getSourceSubtaskIndex());
                assertEquals(5, context.getDownstreamParallelism());
                return 4;
            }
        });
        operator.setup(taskInfo(3, 4));
        List<PartitioningEvent> events = new ArrayList<>();
        operator.processElement(new CreateTableEvent(TABLE, SCHEMA), events::add);
        operator.processElement(DataChangeEvent.insertEvent(TABLE, GenericRowData.of(1)), events::add);
        assertEquals(4, events.getLast().getTargetPartition());
    }

    @Test
    void invalidSchemaAndEventContractsFailBeforeRoutingRows() throws Exception {
        RegularPrePartitionOperator operator =
                new RegularPrePartitionOperator(2, new DefaultDataChangeEventHashFunctionProvider());
        operator.setup(taskInfo(0, 1));
        assertThrows(IllegalStateException.class,
                () -> operator.processElement(DataChangeEvent.insertEvent(TABLE, GenericRowData.of(1)), value -> {}));
        operator.processElement(new CreateTableEvent(TABLE, SCHEMA), value -> {});
        Schema changed = new Schema(
                List.of(new PhysicalColumn("id", new BasicType(false, LogicalTypeRoot.BIGINT))), List.of("id"));
        assertThrows(IllegalStateException.class,
                () -> operator.processElement(new CreateTableEvent(TABLE, changed), value -> {}));
        assertThrows(UnsupportedOperationException.class, () -> operator.processElement(new Event() {}, value -> {}));
        EventPartitioner<Object> partitioner = new EventPartitioner<>();
        assertThrows(IllegalArgumentException.class, () -> partitioner.selectChannel("unwrapped", 2));
        assertThrows(IllegalArgumentException.class,
                () -> partitioner.selectChannel(PartitioningEvent.ofRegular(new CreateTableEvent(TABLE, SCHEMA), 2), 2));
    }

    private static RuntimeTaskInfo taskInfo(int subtask, int parallelism) {
        return new RuntimeTaskInfo(JobID.generate(), 3, subtask, parallelism, 0, 128);
    }
}
