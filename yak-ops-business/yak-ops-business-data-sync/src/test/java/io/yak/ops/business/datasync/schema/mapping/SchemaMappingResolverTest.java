package io.yak.ops.business.datasync.schema.mapping;

import io.yak.ops.business.datasync.schema.LogicalColumn;
import io.yak.ops.business.datasync.schema.LogicalTable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.flow.api.row.YakTypes;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaMappingResolverTest {

    private final SchemaMappingResolver resolver = new SchemaMappingResolver();

    @Test
    void shouldKeepSameNameMappingWhenTaskMappingIsMissing() {
        ResolvedSchemaMapping result = resolver.resolve(sourceTable(), null);

        assertEquals(List.of("id", "name", "age"), names(result.sourceTable()));
        assertEquals(List.of("id", "name", "age"), names(result.targetTable()));
        assertEquals(List.of("id"), result.sourceTable().primaryKeys());
        assertEquals(List.of("id"), result.targetTable().primaryKeys());
    }

    @Test
    void shouldProjectRenameAndReorderByMappingOrder() {
        ResolvedSchemaMapping result = resolver.resolve(
                sourceTable(),
                List.of(
                        new SchemaColumnMapping("name", "display_name"),
                        new SchemaColumnMapping("id", "user_id")));

        assertEquals(List.of("name", "id"), names(result.sourceTable()));
        assertEquals(List.of("display_name", "user_id"), names(result.targetTable()));
        assertEquals(List.of("id"), result.sourceTable().primaryKeys());
        assertEquals(List.of("user_id"), result.targetTable().primaryKeys());
    }

    @Test
    void shouldAllowSourceSubset() {
        ResolvedSchemaMapping result =
                resolver.resolve(sourceTable(), List.of(new SchemaColumnMapping("age", "user_age")));

        assertEquals(List.of("age"), names(result.sourceTable()));
        assertEquals(List.of("user_age"), names(result.targetTable()));
        assertEquals(List.of(), result.sourceTable().primaryKeys());
        assertEquals(List.of(), result.targetTable().primaryKeys());
    }

    @Test
    void shouldNotProjectPartialCompositePrimaryKey() {
        LogicalTable source = new LogicalTable(
                "orders",
                "orders",
                1,
                List.of(
                        new LogicalColumn("tenant_id", YakTypes.BIGINT, false, null, "tenant"),
                        new LogicalColumn("id", YakTypes.BIGINT, false, null, "id"),
                        new LogicalColumn("amount", YakTypes.INTEGER, true, null, "amount")),
                List.of("tenant_id", "id"));

        ResolvedSchemaMapping result =
                resolver.resolve(source, List.of(new SchemaColumnMapping("id", "order_id")));

        assertEquals(List.of(), result.sourceTable().primaryKeys());
        assertEquals(List.of(), result.targetTable().primaryKeys());
    }

    @Test
    void shouldResolveSourceNameCaseInsensitively() {
        ResolvedSchemaMapping result =
                resolver.resolve(sourceTable(), List.of(new SchemaColumnMapping("ID", "USER_ID")));

        assertEquals(List.of("id"), names(result.sourceTable()));
        assertEquals(List.of("USER_ID"), names(result.targetTable()));
        assertEquals(List.of("USER_ID"), result.targetTable().primaryKeys());
    }

    @Test
    void shouldRejectMissingSourceField() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(
                        sourceTable(), List.of(new SchemaColumnMapping("missing", "target_field"))));

        assertEquals("来源表不存在映射字段：missing", exception.getMessage());
    }

    private LogicalTable sourceTable() {
        return new LogicalTable(
                "users",
                "users",
                1,
                List.of(
                        new LogicalColumn("id", YakTypes.BIGINT, false, null, "id"),
                        new LogicalColumn("name", YakTypes.STRING, true, 100, "name"),
                        new LogicalColumn("age", YakTypes.INTEGER, true, null, "age")),
                List.of("id"));
    }

    private List<String> names(LogicalTable table) {
        return table.columns().stream().map(LogicalColumn::name).toList();
    }
}
