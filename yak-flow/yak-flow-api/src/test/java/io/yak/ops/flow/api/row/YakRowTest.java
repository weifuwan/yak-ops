package io.yak.ops.flow.api.row;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class YakRowTest {

    @Test
    void shouldKeepNullValuesAndRowKind() {
        YakRow row = new YakRow(RowKind.UPDATE_AFTER, Arrays.asList(1L, null, "yak"));

        assertEquals(RowKind.UPDATE_AFTER, row.rowKind());
        assertEquals(3, row.arity());
        assertEquals(1L, row.value(0));
        assertNull(row.value(1));
        assertEquals("yak", row.value(2));
    }

    @Test
    void shouldDefensivelyCopyValues() {
        List<Object> values = new ArrayList<>(List.of(1L, "before"));
        YakRow row = new YakRow(RowKind.INSERT, values);

        values.set(1, "after");

        assertEquals("before", row.value(1));
        assertThrows(UnsupportedOperationException.class, () -> row.values().add("new"));
    }
}
