package io.yak.ops.connector.jdbc.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.factory.TableSinkFactoryContext;
import io.yak.ops.connector.base.sink.TableEventSink;
import io.yak.ops.connector.jdbc.database.JdbcFactoryLoader;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.core.api.common.TaskInfo;
import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.api.connector.sink.SchemaChangeAwareSinkWriter;
import io.yak.ops.core.api.connector.sink.SchemaEvolutionSink;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.event.DropColumnEvent;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.event.SchemaChangeEventType;
import io.yak.ops.core.event.SchemaChangeRequest;
import io.yak.ops.core.event.SourceSchemaChange;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.schema.SchemaVersionedTableState;
import io.yak.ops.core.types.LogicalTypes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class ProjectedEventSchemaTest {

    private static final TableId SOURCE = new TableId("source", null, "input");
    private static final TableId TARGET = new TableId(null, "PUBLIC", "OUTPUT");
    private static final PhysicalColumn ID = new PhysicalColumn("ID", LogicalTypes.BIGINT.copy(false));
    private static final PhysicalColumn NAME = new PhysicalColumn("NAME", LogicalTypes.varchar(20));
    private static final Schema RAW = new Schema(List.of(ID, NAME), List.of("ID"));
    private static final Schema ONLY_ID = new Schema(List.of(ID), List.of("ID"));
    private static final SinkWriter.Context RECORD_CONTEXT = new SinkWriter.Context() {
        @Override
        public Long timestamp() { return null; }

        @Override
        public long currentWatermark() { return Long.MIN_VALUE; }
    };

    @Test
    void renamedSourceColumnDropsItsProjectedTargetAndRestoresRawSchemaState() throws Exception {
        BiFunction<TableId, Schema, Schema> rename = (table, schema) -> {
            List<PhysicalColumn> columns = new ArrayList<>();
            for (PhysicalColumn column : schema.columns()) {
                columns.add(column.name().equals("NAME")
                        ? new PhysicalColumn("TITLE", column.type(), column.comment(), column.properties()) : column);
            }
            return new Schema(columns, schema.primaryKeys(), schema.comment());
        };
        try (Database database = new Database("CREATE TABLE \"PUBLIC\".\"OUTPUT\" (\"ID\" BIGINT PRIMARY KEY, \"TITLE\" VARCHAR(20))")) {
            Sink<Event> sink = database.eventSink(rename, RAW);
            SchemaEvolutionSink evolving = (SchemaEvolutionSink) sink;
            SchemaChangeRequest request;
            try (SinkWriter<Event> writer = sink.createWriter(context())) {
                writer.write(new CreateTableEvent(TARGET, rename.apply(SOURCE, RAW)), RECORD_CONTEXT);
                writer.write(DataChangeEvent.insertEvent(TARGET, GenericRowData.of(1L, "before")), RECORD_CONTEXT);
                writer.flush(false);
                SourceSchemaChange change = new SourceSchemaChange("binlog:100", SOURCE, RAW, ONLY_ID);
                assertThrows(UnsupportedOperationException.class, () -> evolving.planSchemaChange(change, false));
                request = evolving.planSchemaChange(change, true);
                assertEquals(RAW, request.sourceSchemaBefore());
                assertEquals(ONLY_ID, request.sourceSchemaAfter());
                DropColumnEvent drop = assertInstanceOf(DropColumnEvent.class, request.targetChanges().getFirst());
                assertEquals(List.of("TITLE"), drop.droppedColumnNames());
                apply(evolving, writer, request);
                writer.write(drop, RECORD_CONTEXT);
                writer.write(DataChangeEvent.insertEvent(TARGET, GenericRowData.of(2L)), RECORD_CONTEXT);
                writer.flush(false);
            }
            assertEquals(List.of(1L, 2L), database.ids());

            Sink<Event> restoredSink = database.eventSink(rename, RAW);
            ((SchemaEvolutionSink) restoredSink).restoreSchemaState(List.of(new SchemaVersionedTableState(
                    SOURCE, TARGET, ONLY_ID, request.targetSchemaAfter(), 1)));
            try (SinkWriter<Event> writer = restoredSink.createWriter(context())) {
                writer.write(new CreateTableEvent(TARGET, rename.apply(SOURCE, ONLY_ID)), RECORD_CONTEXT);
                writer.write(DataChangeEvent.insertEvent(TARGET, GenericRowData.of(3L)), RECORD_CONTEXT);
                writer.flush(false);
            }
            assertEquals(List.of(1L, 2L, 3L), database.ids());
        }
    }

    @Test
    void anExcludedColumnChangeAdvancesRawSchemaWithoutTargetDdlOrWriterShapeChanges() throws Exception {
        BiFunction<TableId, Schema, Schema> project = (table, schema) ->
                new Schema(List.of(schema.column("ID").orElseThrow()), List.of("ID"));
        Schema after = new Schema(List.of(ID, NAME, new PhysicalColumn("EXCLUDED", LogicalTypes.varchar(32))), List.of("ID"));
        try (Database database = new Database("CREATE TABLE \"PUBLIC\".\"OUTPUT\" (\"ID\" BIGINT PRIMARY KEY)")) {
            Sink<Event> sink = database.eventSink(project, RAW);
            SchemaEvolutionSink evolving = (SchemaEvolutionSink) sink;
            SchemaChangeRequest request = evolving.planSchemaChange(new SourceSchemaChange("binlog:200", SOURCE, RAW, after));
            assertEquals(RAW, request.sourceSchemaBefore());
            assertEquals(after, request.sourceSchemaAfter());
            assertTrue(request.targetChanges().isEmpty());
            assertEquals(request.targetSchemaBefore(), request.targetSchemaAfter());
            try (SinkWriter<Event> writer = sink.createWriter(context())) {
                writer.write(new CreateTableEvent(TARGET, ONLY_ID), RECORD_CONTEXT);
                writer.write(DataChangeEvent.insertEvent(TARGET, GenericRowData.of(1L)), RECORD_CONTEXT);
                apply(evolving, writer, request);
                writer.write(DataChangeEvent.insertEvent(TARGET, GenericRowData.of(2L)), RECORD_CONTEXT);
                writer.flush(false);
            }
            Sink<Event> restored = database.eventSink(project, RAW);
            ((SchemaEvolutionSink) restored).restoreSchemaState(List.of(new SchemaVersionedTableState(
                    SOURCE, TARGET, after, ONLY_ID, 1)));
            try (SinkWriter<Event> writer = restored.createWriter(context())) {
                writer.write(new CreateTableEvent(TARGET, ONLY_ID), RECORD_CONTEXT);
                writer.write(DataChangeEvent.insertEvent(TARGET, GenericRowData.of(3L)), RECORD_CONTEXT);
                writer.flush(false);
            }
            assertEquals(List.of(1L, 2L, 3L), database.ids());
        }
    }

    @SuppressWarnings("unchecked")
    private static void apply(SchemaEvolutionSink sink, SinkWriter<Event> writer, SchemaChangeRequest request) throws Exception {
        SchemaChangeAwareSinkWriter<Event> aware = (SchemaChangeAwareSinkWriter<Event>) writer;
        aware.validateSchemaChange(request);
        writer.flush(false);
        try (MetadataApplier applier = sink.createMetadataApplier()) {
            applier.setAcceptedSchemaEvolutionTypes(Set.of(SchemaChangeEventType.DROP_COLUMN));
            for (io.yak.ops.core.event.SchemaChangeEvent event : request.targetChanges()) {
                applier.applySchemaChange(event);
            }
        }
        aware.onSchemaChange(request, 1);
        sink.schemaChangeCompleted(request);
    }

    private static WriterInitContext context() {
        return new WriterInitContext() {
            @Override
            public TaskInfo getTaskInfo() {
                throw new UnsupportedOperationException("Task identity is not used by this JDBC writer");
            }

            @Override
            public Configuration getConfiguration() {
                return new Configuration();
            }
        };
    }

    private static final class Database implements AutoCloseable {
        private final String url = "jdbc:h2:mem:event_schema_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        private final JdbcConnectionProvider connections = () -> DriverManager.getConnection(url, "sa", "");
        private final JdbcDialect dialect = JdbcFactoryLoader.loadDialect("jdbc:postgresql:");

        private Database(String ddl) throws Exception {
            execute(ddl);
        }

        private Sink<Event> eventSink(BiFunction<TableId, Schema, Schema> projection, Schema raw) {
            Schema projected = projection.apply(SOURCE, raw);
            JdbcSink jdbc = JdbcSink.builder().withConnectionProvider(connections).withDialect(dialect)
                    .withSourceTable(SOURCE).withTargetTable(TARGET).withSchema(projected.toTableSchema())
                    .withMetadataApplierFactory(() -> new JdbcMetadataApplier(connections, dialect,
                            JdbcFactoryLoader.loadCatalog(url, connections), 30)).build();
            return TableEventSink.of(jdbc, List.of(new TableSinkFactoryContext(new Configuration().toReadonlyConfig(), SOURCE,
                    projected.toTableSchema(), TARGET, projected.toTableSchema())), projection, Map.of(SOURCE, raw));
        }

        private List<Long> ids() throws Exception {
            List<Long> result = new ArrayList<>();
            try (Connection connection = connections.getConnection(); Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT \"ID\" FROM \"PUBLIC\".\"OUTPUT\" ORDER BY \"ID\"")) {
                while (rows.next()) {
                    result.add(rows.getLong(1));
                }
            }
            return result;
        }

        private void execute(String sql) throws Exception {
            try (Connection connection = connections.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }

        @Override
        public void close() throws Exception {
            execute("SHUTDOWN");
        }
    }
}
