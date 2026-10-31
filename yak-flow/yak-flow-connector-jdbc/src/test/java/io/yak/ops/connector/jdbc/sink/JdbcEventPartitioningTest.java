package io.yak.ops.connector.jdbc.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.factory.TableSinkFactoryContext;
import io.yak.ops.connector.base.sink.TableDataSink;
import io.yak.ops.connector.jdbc.database.JdbcFactoryLoader;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.common.TaskInfo;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.sink.DataSink;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.partitioning.PartitioningEvent;
import io.yak.ops.flow.runtime.partitioning.RegularPrePartitionOperator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

/** Atomic JDBC updates retain table order even when writers flush in an adversarial order. */
class JdbcEventPartitioningTest {
    private static final TableId SOURCE = new TableId("shop", null, "orders");
    private static final TableId TARGET = new TableId("PUBLIC", null, "OUTPUT");
    private static final Schema RAW = new Schema(List.of(
            new PhysicalColumn("id", LogicalTypes.INTEGER.copy(false)),
            new PhysicalColumn("code", LogicalTypes.INTEGER.copy(false))), List.of("id"));
    private static final Schema WRITE = new Schema(RAW.columns(), List.of("code"));
    private static final SinkWriter.Context RECORD_CONTEXT = new SinkWriter.Context() {
        @Override public Long timestamp() { return null; }
        @Override public long currentWatermark() { return Long.MIN_VALUE; }
    };

    @Test
    void aTransformedUniqueKeyUpdateCannotLeaveTheOldRowAfterReverseWriterFlushes() throws Exception {
        assertAtomicKeyChanges(WRITE, (table, schema) -> new Schema(schema.columns(), List.of("code")));
    }

    @Test
    void targetWriteKeyOverridesAndKeyReuseRemainOrderedAcrossDifferentSourcePrimaryKeys() throws Exception {
        assertAtomicKeyChanges(RAW, (table, schema) -> schema);
    }

    private static void assertAtomicKeyChanges(
            Schema eventSchema, BiFunction<TableId, Schema, Schema> transform) throws Exception {
        String url = "jdbc:h2:mem:partition_order_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        JdbcConnectionProvider connections = () -> DriverManager.getConnection(url, "sa", "");
        try (Connection connection = connections.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE PUBLIC.OUTPUT (id INT NOT NULL, code INT PRIMARY KEY)");
        }
        JdbcSink jdbc = JdbcSink.builder().withConnectionProvider(connections)
                .withDialect(JdbcFactoryLoader.loadDialect("jdbc:mysql:"))
                .withSourceTable(SOURCE).withTargetTable(TARGET).withSchema(WRITE.toTableSchema())
                .withWriteMode(JdbcWriteMode.UPSERT).build();
        DataSink sink = new TableDataSink(jdbc,
                List.of(new TableSinkFactoryContext(new Configuration().toReadonlyConfig(), SOURCE,
                        WRITE.toTableSchema(), TARGET, WRITE.toTableSchema())),
                () -> { throw new AssertionError("This data-only regression must not create a metadata applier"); },
                transform, Map.of(SOURCE, RAW), new JdbcDataChangeEventHashFunctionProvider(true));
        RegularPrePartitionOperator partition = new RegularPrePartitionOperator(
                2, sink.getDataChangeEventHashFunctionProvider(2));
        partition.setup(new RuntimeTaskInfo(JobID.generate(), 1, 0, 1, 0, 128));
        try (SinkWriter<Event> first = sink.getEventSinkProvider().getSink().createWriter(context(0));
                SinkWriter<Event> second = sink.getEventSinkProvider().getSink().createWriter(context(1))) {
            List<SinkWriter<Event>> writers = List.of(first, second);
            partition.processElement(new CreateTableEvent(TARGET, eventSchema),
                    event -> writers.get(event.getTargetPartition()).write(event.getPayload(), RECORD_CONTEXT));
            int insertWriter = emit(partition, writers, DataChangeEvent.insertEvent(TARGET, GenericRowData.of(7, 1)));
            DataChangeEvent update = DataChangeEvent.updateEvent(
                    TARGET, GenericRowData.of(7, 1), GenericRowData.of(7, 2), Map.of("origin", "binlog"))
                    .withChangeEventId(new ChangeEventId("source", "update-1", 0, 2));
            int updateWriter = emit(partition, writers, update);
            flushFirst(writers, updateWriter);
            assertEquals(List.of(List.of(7, 2)), rows(connections), "The old key must not reappear after the update");
            assertEquals(insertWriter, updateWriter);

            // The previous code=2 is reused by another physical source id. Hashing raw id would
            // permit the new INSERT to commit before the old row's DELETE and lose the new row.
            int movedWriter = emit(partition, writers, DataChangeEvent.updateEvent(
                    TARGET, GenericRowData.of(7, 2), GenericRowData.of(7, 4))
                    .withChangeEventId(new ChangeEventId("source", "update-2", 0, 2)));
            int reusedWriter = emit(partition, writers, DataChangeEvent.insertEvent(TARGET, GenericRowData.of(8, 2)));
            flushFirst(writers, reusedWriter);
            assertEquals(List.of(List.of(8, 2), List.of(7, 4)), rows(connections),
                    "Deleting the old key must precede its reuse by another source row");
            assertEquals(movedWriter, reusedWriter);
        }
    }

    private static int emit(RegularPrePartitionOperator partition, List<SinkWriter<Event>> writers,
            DataChangeEvent change) throws Exception {
        List<PartitioningEvent> routed = new ArrayList<>();
        partition.processElement(change, routed::add);
        assertEquals(1, routed.size());
        PartitioningEvent event = routed.getFirst();
        assertSame(change, event.getPayload(), "Partitioning must preserve the complete event and its image identity");
        writers.get(event.getTargetPartition()).write(event.getPayload(), RECORD_CONTEXT);
        return event.getTargetPartition();
    }

    private static void flushFirst(List<SinkWriter<Event>> writers, int first) throws Exception {
        writers.get(first).flush(false);
        writers.get(1 - first).flush(false);
    }

    private static List<List<Integer>> rows(JdbcConnectionProvider connections) throws Exception {
        List<List<Integer>> values = new ArrayList<>();
        try (Connection connection = connections.getConnection(); Statement sql = connection.createStatement();
                ResultSet rows = sql.executeQuery("SELECT id, code FROM PUBLIC.OUTPUT ORDER BY code")) {
            while (rows.next()) values.add(List.of(rows.getInt(1), rows.getInt(2)));
        }
        return values;
    }

    private static WriterInitContext context(int subtask) {
        return new WriterInitContext() {
            @Override public TaskInfo getTaskInfo() {
                return new RuntimeTaskInfo(JobID.generate(), 2, subtask, 2, 0, 128);
            }
            @Override public Configuration getConfiguration() { return new Configuration(); }
        };
    }
}
