package io.yak.ops.core.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class TableSchemaTest {

    @Test
    void preservesColumnOrderAndCompositePrimaryKeySequence() {
        TableSchema schema = new TableSchema(
                List.of(new Column("TENANT", LogicalTypes.INTEGER, false, null),
                        new Column("ID", LogicalTypes.BIGINT, false, null),
                        new Column("BODY", LogicalTypes.STRING, true, 64)),
                List.of("TENANT", "ID"));
        assertEquals(3, schema.columnCount());
        assertEquals("ID", schema.column(1).name());
        assertEquals(List.of("TENANT", "ID"), schema.primaryKeys());
    }

    @Test
    void rejectsDuplicateColumnsAndUnknownPrimaryKeys() {
        Column id = new Column("id", LogicalTypes.BIGINT, false, null);
        assertThrows(IllegalArgumentException.class, () -> new TableSchema(List.of(id, id), List.of("id")));
        assertThrows(IllegalArgumentException.class, () -> new TableSchema(List.of(id), List.of("missing")));
        assertThrows(IllegalArgumentException.class, () -> new TableSchema(List.of(id), List.of("id", "id")));
    }
}
