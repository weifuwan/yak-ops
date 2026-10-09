package io.yak.ops.business.datasync.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.flow.api.row.YakTypeKind;
import io.yak.ops.flow.api.row.YakTypes;
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
                        new LogicalColumn("id", YakTypes.BIGINT, false, null, "主键"),
                        new LogicalColumn("name", YakTypes.STRING, true, 128, "名称"),
                        new LogicalColumn("amount", YakTypes.decimal(18, 2), true, null, "金额")),
                List.of("id"));

        var runtime = table.toRuntimeSchema();

        assertEquals(3, runtime.columnCount());
        assertEquals(List.of("id"), runtime.primaryKeys());
        assertEquals(YakTypeKind.BIGINT, runtime.column(0).dataType().kind());
        assertEquals(128, runtime.column(1).length());
        assertEquals(YakTypeKind.DECIMAL, runtime.column(2).dataType().kind());
    }

    @Test
    void shouldPreserveCompositePrimaryKeyOrder() {
        LogicalTable table = new LogicalTable(
                "order_items",
                null,
                2,
                List.of(
                        new LogicalColumn("order_id", YakTypes.BIGINT, false, null, null),
                        new LogicalColumn("item_id", YakTypes.BIGINT, false, null, null)),
                List.of("order_id", "item_id"));

        assertEquals(List.of("order_id", "item_id"), table.primaryKeys());
        assertEquals(List.of("order_id", "item_id"), table.toRuntimeSchema().primaryKeys());
    }

    @Test
    void shouldRejectPrimaryKeyOutsideLogicalColumns() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LogicalTable(
                        "orders",
                        null,
                        1,
                        List.of(new LogicalColumn("id", YakTypes.BIGINT, false, null, null)),
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
                                new LogicalColumn("id", YakTypes.BIGINT, false, null, null),
                                new LogicalColumn("id", YakTypes.BIGINT, true, null, null)),
                        List.of()));
    }

    @Test
    void shouldRejectCapacityOnNonCapacityType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LogicalColumn("id", YakTypes.BIGINT, false, 20, null));
    }
}
