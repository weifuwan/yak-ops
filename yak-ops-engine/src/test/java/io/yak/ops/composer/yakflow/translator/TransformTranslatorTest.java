package io.yak.ops.composer.yakflow.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cli.parser.YamlPipelineDefinitionParser;
import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.composer.definition.TransformDef;
import io.yak.ops.connector.jdbc.sink.JdbcSinkSchemaValidator;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.AddColumnEvent;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.event.OperationType;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.flow.runtime.operators.OneInputStreamOperator;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransformTranslatorTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");
    private static final Schema SCHEMA = new Schema(List.of(
            new PhysicalColumn("id", LogicalTypes.INTEGER.copy(false)),
            new PhysicalColumn("name", LogicalTypes.STRING),
            new PhysicalColumn("amount", LogicalTypes.INTEGER)), List.of("id"));

    @Test
    void projectionAndFilterUseTheUpstreamSqlProcessors() throws Exception {
        OneInputStreamOperator<Event, Event> operator = translator("id, UPPER(name) AS label, amount * 2 AS total", "total > 10").createOperator();
        List<Event> events = new ArrayList<>();
        operator.processElement(new CreateTableEvent(TABLE, SCHEMA), events::add);
        operator.processElement(DataChangeEvent.insertEvent(TABLE, GenericRowData.of(1, "alice", 6)), events::add);
        operator.processElement(DataChangeEvent.insertEvent(TABLE, GenericRowData.of(2, "bob", 4)), events::add);
        assertEquals(2, events.size());
        CreateTableEvent metadata = (CreateTableEvent) events.getFirst();
        assertEquals(List.of("id", "label", "total"), metadata.getPhysicalSchema().columns().stream().map(PhysicalColumn::name).toList());
        assertEquals(List.of("id"), metadata.getSchema().primaryKeys());
        assertEquals(GenericRowData.of(1, "ALICE", 12), ((DataChangeEvent) events.getLast()).after());
    }

    @Test
    void updatesCrossingTheFilterBoundaryBecomeInsertOrDeleteWithReplayIdentity() throws Exception {
        OneInputStreamOperator<Event, Event> operator = translator("*", "amount > 10").createOperator();
        List<Event> events = new ArrayList<>();
        operator.processElement(new CreateTableEvent(TABLE, SCHEMA), events::add);
        ChangeEventId identity = new ChangeEventId("source", "binlog:400", 0, 2);
        DataChangeEvent entering = DataChangeEvent.updateEvent(TABLE,
                GenericRowData.of(1, "alice", 2), GenericRowData.of(1, "alice", 12), Map.of("origin", "binlog"))
                .withChangeEventId(identity);
        operator.processElement(entering, events::add);
        DataChangeEvent inserted = (DataChangeEvent) events.getLast();
        assertEquals(OperationType.INSERT, inserted.op());
        assertNull(inserted.before());
        assertEquals(Map.of("origin", "binlog"), inserted.meta());
        assertTrue(identity.sameEvent(inserted.changeEventId()));
        assertEquals(1, inserted.changeEventId().imageCount());
        operator.processElement(DataChangeEvent.updateEvent(TABLE,
                GenericRowData.of(1, "alice", 12), GenericRowData.of(1, "alice", 3)).withChangeEventId(identity), events::add);
        assertEquals(OperationType.DELETE, ((DataChangeEvent) events.getLast()).op());
        operator.processElement(DataChangeEvent.updateEvent(TABLE,
                GenericRowData.of(1, "alice", 12), GenericRowData.of(1, "alice", 13)).withChangeEventId(identity), events::add);
        assertEquals(OperationType.UPDATE, ((DataChangeEvent) events.getLast()).op());
        assertEquals(identity, ((DataChangeEvent) events.getLast()).changeEventId());
    }

    @Test
    void sqlNullAndUnselectedColumnsRetainFilterSemantics() throws Exception {
        OneInputStreamOperator<Event, Event> operator = translator("id, name", "amount > 10").createOperator();
        List<Event> events = new ArrayList<>();
        operator.processElement(new CreateTableEvent(TABLE, SCHEMA), events::add);
        operator.processElement(DataChangeEvent.insertEvent(TABLE, GenericRowData.of(1, "alice", null)), events::add);
        operator.processElement(DataChangeEvent.insertEvent(TABLE, GenericRowData.of(2, "bob", 11)), events::add);
        assertEquals(2, events.size());
        assertEquals(GenericRowData.of(2, "bob"), ((DataChangeEvent) events.getLast()).after());
    }

    @Test
    void wildcardSchemaEvolutionRebuildsProjectionBeforeTheNextRow() throws Exception {
        OneInputStreamOperator<Event, Event> operator = translator("*", null).createOperator();
        List<Event> events = new ArrayList<>();
        operator.processElement(new CreateTableEvent(TABLE, SCHEMA), events::add);
        operator.processElement(new AddColumnEvent(TABLE, List.of(AddColumnEvent.last(new PhysicalColumn("active", LogicalTypes.BOOLEAN)))), events::add);
        operator.processElement(DataChangeEvent.insertEvent(TABLE, GenericRowData.of(1, "alice", 10, true)), events::add);
        assertEquals(3, events.size());
        assertTrue(events.get(1) instanceof AddColumnEvent);
        assertEquals(GenericRowData.of(1, "alice", 10, true), ((DataChangeEvent) events.getLast()).after());
    }

    @Test
    void changesToAnExcludedColumnDoNotInventTargetDdl() throws Exception {
        OneInputStreamOperator<Event, Event> operator = translator("id, name", null).createOperator();
        List<Event> events = new ArrayList<>();
        operator.processElement(new CreateTableEvent(TABLE, SCHEMA), events::add);
        operator.processElement(new AddColumnEvent(TABLE, List.of(AddColumnEvent.last(new PhysicalColumn("active", LogicalTypes.BOOLEAN)))), events::add);
        assertEquals(1, events.size());
        operator.processElement(DataChangeEvent.insertEvent(TABLE, GenericRowData.of(1, "alice", 10, true)), events::add);
        assertEquals(GenericRowData.of(1, "alice"), ((DataChangeEvent) events.getLast()).after());
    }

    @Test
    void metadataColumnsAndFirstMatchingRuleFollowFlinkCdc() throws Exception {
        PipelineDef definition = definition("id, __table_name__ AS source_table", null);
        definition.getTransforms().add(new io.yak.ops.composer.definition.TransformDef("shop.orders", "id", "id < 0", null, null, null, null, null));
        TransformTranslator translator = new TransformTranslator(definition);
        OneInputStreamOperator<Event, Event> operator = translator.createOperator();
        List<Event> events = new ArrayList<>();
        operator.processElement(new CreateTableEvent(TABLE, SCHEMA), events::add);
        operator.processElement(DataChangeEvent.insertEvent(TABLE, GenericRowData.of(7, "alice", 10)), events::add);
        assertEquals(GenericRowData.of(7, "orders"), ((DataChangeEvent) events.getLast()).after());
    }

    @Test
    void removingPartOfACompositeKeyNeverCreatesAFalseUniqueIdentity() {
        Schema composite = new Schema(SCHEMA.columns(), List.of("id", "name"));
        Schema projected = translator("id, amount", null).transformSchema(TABLE, composite);
        assertTrue(projected.primaryKeys().isEmpty());
        Schema unsafeTarget = new Schema(projected.columns(), List.of("id"));
        assertThrows(IllegalArgumentException.class, () -> JdbcSinkSchemaValidator.validate(
                projected.toTableSchema(), unsafeTarget.toTableSchema(), JdbcWriteMode.UPSERT));
        assertEquals(composite.primaryKeys(), translator("*", null).transformSchema(TABLE, composite).primaryKeys());
    }

    @Test
    void explicitTransformKeysRequireCompleteTraceablePhysicalUniqueness() {
        Schema composite = new Schema(List.of(
                new PhysicalColumn("id", LogicalTypes.INTEGER.copy(false)),
                new PhysicalColumn("tenant", LogicalTypes.INTEGER.copy(false)),
                new PhysicalColumn("email", LogicalTypes.INTEGER.copy(false))), List.of("id", "tenant"));
        DataSourceTable source = new DataSourceTable("MYSQL", TABLE, composite.toTableSchema(), 1,
                null, Map.of(), Set.of(), Map.of(), List.of(List.of("email")));
        assertEquals(List.of("key_id", "tenant"), withKeys("id AS key_id, tenant", "key_id, tenant")
                .transformMetadata(source).schema().primaryKeys());
        assertEquals(List.of("email_key"), withKeys("email AS email_key", "email_key")
                .transformMetadata(source).schema().primaryKeys());
        assertThrows(IllegalArgumentException.class, () -> withKeys("id, email", "id").transformMetadata(source));
        assertThrows(IllegalArgumentException.class, () -> withKeys("id * 0 AS key_id", "key_id").transformMetadata(source));
        assertThrows(IllegalArgumentException.class, () -> withKeys("id AS first_id, id AS second_id", "first_id, second_id")
                .transformMetadata(source));
        DataSourceTable nullable = new DataSourceTable("MYSQL", TABLE, SCHEMA.toTableSchema(), 1,
                null, Map.of(), Set.of(), Map.of(), List.of(List.of("name")));
        assertThrows(IllegalArgumentException.class, () -> withKeys("name", "name").transformMetadata(nullable));
    }

    @Test
    void selectorsMatchTheAdmittedTableAfterDatasourceDefaultsCompleteItsNamespace() throws Exception {
        TableId physical = new TableId("application", "shop", "orders");
        TransformTranslator translator = new TransformTranslator(definition("id, UPPER(name) AS label", "amount > 10"),
                Map.of(physical, new TableId(null, "shop", "orders")));
        DataSourceTable projected = translator.transformMetadata(new DataSourceTable("POSTGRE_SQL", physical, SCHEMA.toTableSchema(), 1));
        assertEquals(List.of("id", "label"), projected.schema().columns().stream().map(io.yak.ops.core.types.Column::name).toList());
        OneInputStreamOperator<Event, Event> operator = translator.createOperator();
        List<Event> events = new ArrayList<>();
        operator.processElement(new CreateTableEvent(physical, SCHEMA), events::add);
        operator.processElement(DataChangeEvent.insertEvent(physical, GenericRowData.of(1, "alice", 11)), events::add);
        operator.processElement(DataChangeEvent.insertEvent(physical, GenericRowData.of(2, "bob", 3)), events::add);
        assertEquals(2, events.size());
        assertEquals(physical, ((DataChangeEvent) events.getLast()).tableId());
        assertEquals(GenericRowData.of(1, "ALICE"), ((DataChangeEvent) events.getLast()).after());
    }

    @Test
    void replaceMetadataUsesTheUpstreamUpdateAfterOperationType() throws Exception {
        OneInputStreamOperator<Event, Event> operator = translator("id, __data_event_type__ AS operation", "__data_event_type__ = '+U'")
                .createOperator();
        List<Event> events = new ArrayList<>();
        operator.processElement(new CreateTableEvent(TABLE, SCHEMA), events::add);
        operator.processElement(DataChangeEvent.replaceEvent(TABLE, GenericRowData.of(4, "alice", 20)), events::add);
        assertEquals(2, events.size());
        assertEquals(OperationType.REPLACE, ((DataChangeEvent) events.getLast()).op());
        assertEquals(GenericRowData.of(4, "+U"), ((DataChangeEvent) events.getLast()).after());
    }

    private static TransformTranslator withKeys(String projection, String keys) {
        PipelineDef definition = definition(projection, null);
        definition.getTransforms().set(0, new TransformDef("shop.orders", projection, null, keys, null, null, null, null));
        return new TransformTranslator(definition);
    }

    private static TransformTranslator translator(String projection, String filter) {
        return new TransformTranslator(definition(projection, filter));
    }

    private static PipelineDef definition(String projection, String filter) {
        return new YamlPipelineDefinitionParser().parse("""
                source:
                  type: jdbc-mysql
                  datasource-id: source
                  tables: shop.orders
                sink:
                  type: jdbc-postgres
                  datasource-id: target
                pipeline:
                  execution.runtime-mode: BATCH
                  local-time-zone: UTC
                transform:
                  - source-table: shop.orders
                    projection: "%s"
                %s
                """.formatted(projection, filter == null ? "" : "    filter: \"" + filter + "\""));
    }
}
