package io.yak.ops.business.datasync.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.LogicalTypes;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogicalTableContractTest {

    @Test
    void shouldProjectProductLogicalTableToRuntimeSchema() {
        LogicalTable table = new LogicalTable(
                "orders",
                "订单",
                1,
                List.of(
                        new LogicalColumn("id", LogicalTypes.BIGINT, false, null, "主键"),
                        new LogicalColumn("name", LogicalTypes.STRING, true, 128, "名称"),
                        new LogicalColumn("amount", LogicalTypes.decimal(18, 2), true, null, "金额")),
                List.of("id"));

        var runtime = table.toTableSchema();

        assertEquals(3, runtime.columnCount());
        assertEquals(List.of("id"), runtime.primaryKeys());
        assertEquals(LogicalTypeRoot.BIGINT, runtime.column(0).dataType().getTypeRoot());
        assertEquals(128, runtime.column(1).length());
        assertEquals(LogicalTypeRoot.DECIMAL, runtime.column(2).dataType().getTypeRoot());
    }

    @Test
    void shouldPreserveCompositePrimaryKeyOrder() {
        LogicalTable table = new LogicalTable(
                "order_items",
                null,
                2,
                List.of(
                        new LogicalColumn("order_id", LogicalTypes.BIGINT, false, null, null),
                        new LogicalColumn("item_id", LogicalTypes.BIGINT, false, null, null)),
                List.of("order_id", "item_id"));

        assertEquals(List.of("order_id", "item_id"), table.primaryKeys());
        assertEquals(List.of("order_id", "item_id"), table.toTableSchema().primaryKeys());
    }

    @Test
    void shouldRejectPrimaryKeyOutsideLogicalColumns() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LogicalTable(
                        "orders",
                        null,
                        1,
                        List.of(new LogicalColumn("id", LogicalTypes.BIGINT, false, null, null)),
                        List.of("missing")));
    }

    @Test
    void shouldRejectDuplicateLogicalColumns() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LogicalTable(
                        "orders",
                        null,
                        1,
                        List.of(
                                new LogicalColumn("id", LogicalTypes.BIGINT, false, null, null),
                                new LogicalColumn("id", LogicalTypes.BIGINT, true, null, null)),
                        List.of()));
    }

    @Test
    void shouldRejectCapacityOnNonCapacityType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LogicalColumn("id", LogicalTypes.BIGINT, false, 20, null));
    }
}
