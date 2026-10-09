package io.yak.ops.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RowDataTest {

    @Test
    void keepsNullValuesAndIsolatesTheSuppliedList() {
        ArrayList<Object> mutable = new ArrayList<>(Arrays.asList(1L, null, "first"));
        RowData row = new RowData(mutable);
        mutable.set(2, "changed");
        assertEquals("first", row.getField(2));
        assertEquals(null, row.getField(1));
        assertEquals(3, row.arity());
        assertThrows(UnsupportedOperationException.class, () -> row.values().add("new"));
    }

    @Test
    void tableRecordCarriesPhysicalIdentityAndChangeKind() {
        TableId table = new TableId("store", "public", "orders");
        TableRecord record = new TableRecord(table, RowKind.UPDATE_AFTER, new RowData(Arrays.asList(1, "updated")));
        assertEquals(table, record.tableId());
        assertEquals(RowKind.UPDATE_AFTER, record.rowKind());
    }
}
