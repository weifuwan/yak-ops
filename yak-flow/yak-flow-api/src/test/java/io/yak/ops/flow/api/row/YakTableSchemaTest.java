package io.yak.ops.flow.api.row;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class YakTableSchemaTest {

    @Test
    void shouldKeepColumnOrderAndPrimaryKeysImmutable() {
        List<YakColumn> columns = new ArrayList<>();
        columns.add(new YakColumn("id", YakTypes.BIGINT, false, null));
        columns.add(new YakColumn("name", YakTypes.STRING, true, 128));
        List<String> primaryKeys = new ArrayList<>(List.of("id"));

        YakTableSchema schema = new YakTableSchema(columns, primaryKeys);
        columns.clear();
        primaryKeys.clear();

        assertEquals(2, schema.columnCount());
        assertEquals("id", schema.column(0).name());
        assertEquals(List.of("id"), schema.primaryKeys());
        assertThrows(UnsupportedOperationException.class, () -> schema.columns().clear());
    }

    @Test
    void shouldRejectEmptyColumns() {
        assertThrows(IllegalArgumentException.class, () -> new YakTableSchema(List.of(), List.of()));
    }
}
