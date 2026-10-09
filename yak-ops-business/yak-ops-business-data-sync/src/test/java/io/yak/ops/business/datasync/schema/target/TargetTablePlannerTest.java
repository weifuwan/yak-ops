package io.yak.ops.business.datasync.schema.target;

import io.yak.ops.business.datasync.schema.LogicalColumn;
import io.yak.ops.business.datasync.schema.LogicalTable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.types.LogicalTypes;
import java.util.List;
import org.junit.jupiter.api.Test;

class TargetTablePlannerTest {

    private final TargetTablePlanner planner = new TargetTablePlanner();

    @Test
    void shouldPlanMysqlTargetTable() {
        TargetTablePlan plan = planner.plan(logicalTable(), "MYSQL", "yakflow", null, "orders_copy");

        assertTrue(plan.supported());
        assertEquals("MYSQL", plan.targetType());
        assertEquals(3, plan.logicalSchemaVersion());
        assertEquals(List.of("id"), plan.primaryKeys());
        assertEquals(List.of("BIGINT", "VARCHAR(100)", "DECIMAL(18,2)"), plan.columns().stream()
                .map(TargetColumnPlan::nativeType)
                .toList());
        assertEquals(
                "CREATE TABLE `yakflow`.`orders_copy` (`id` BIGINT NOT NULL COMMENT '主键', "
                        + "`name` VARCHAR(100) NOT NULL COMMENT '名称', `amount` DECIMAL(18,2) COMMENT '金额', "
                        + "PRIMARY KEY (`id`)) COMMENT='订单表'",
                plan.createTableSql());
        assertEquals(List.of(plan.createTableSql()), plan.ddlStatements());
    }

    @Test
    void shouldCanonicalizePostgresqlTargetType() {
        TargetTablePlan plan = planner.plan(logicalTable(), "POSTGRESQL", "yakflow", "public", "orders_copy");

        assertTrue(plan.supported());
        assertEquals("POSTGRE_SQL", plan.targetType());
        assertEquals("NUMERIC(18,2)", plan.columns().get(2).nativeType());
        assertTrue(plan.createTableSql().startsWith("CREATE TABLE \"public\".\"orders_copy\""));
        assertEquals(5, plan.ddlStatements().size());
        assertEquals("COMMENT ON TABLE \"public\".\"orders_copy\" IS '订单表'", plan.ddlStatements().get(1));
        assertEquals(
                "COMMENT ON COLUMN \"public\".\"orders_copy\".\"id\" IS '主键'",
                plan.ddlStatements().get(2));
    }

    @Test
    void shouldReturnUnsupportedPlanForMysqlTimestampWithTimeZone() {
        LogicalTable table = new LogicalTable(
                "events",
                null,
                1,
                List.of(new LogicalColumn(
                        "event_time", LogicalTypes.TIMESTAMP_WITH_TIME_ZONE, true, null, null)),
                List.of());

        TargetTablePlan plan = planner.plan(table, "MYSQL", "yakflow", null, "events_copy");

        assertFalse(plan.supported());
        assertNull(plan.createTableSql());
        assertTrue(plan.ddlStatements().isEmpty());
        assertEquals(1, plan.unsupportedReasons().size());
        assertTrue(plan.unsupportedReasons().getFirst().contains("TIMESTAMP_WITH_TIME_ZONE"));
    }

    @Test
    void shouldRejectLobBackedPrimaryKeyPlan() {
        LogicalTable table = new LogicalTable(
                "documents",
                null,
                1,
                List.of(new LogicalColumn("document_key", LogicalTypes.STRING, false, null, null)),
                List.of("document_key"));

        TargetTablePlan plan = planner.plan(table, "ORACLE", null, "APP", "documents_copy");

        assertFalse(plan.supported());
        assertEquals("CLOB", plan.columns().getFirst().nativeType());
        assertNull(plan.createTableSql());
        assertTrue(plan.ddlStatements().isEmpty());
        assertTrue(plan.unsupportedReasons().getFirst().contains("不能直接作为目标主键"));
        assertEquals(1, plan.warnings().size());
    }

    @Test
    void shouldKeepSafeWideningWarningWithoutBlockingPlan() {
        LogicalTable table = new LogicalTable(
                "notes",
                null,
                1,
                List.of(new LogicalColumn("content", LogicalTypes.STRING, true, null, null)),
                List.of());

        TargetTablePlan plan = planner.plan(table, "POSTGRE_SQL", null, "public", "notes_copy");

        assertTrue(plan.supported());
        assertEquals("TEXT", plan.columns().getFirst().nativeType());
        assertEquals(1, plan.warnings().size());
        assertTrue(plan.createTableSql().contains("\"content\" TEXT"));
    }

    private LogicalTable logicalTable() {
        return new LogicalTable(
                "orders",
                "订单表",
                3,
                List.of(
                        new LogicalColumn("id", LogicalTypes.BIGINT, false, null, "主键"),
                        new LogicalColumn("name", LogicalTypes.STRING, false, 100, "名称"),
                        new LogicalColumn("amount", LogicalTypes.decimal(18, 2), true, null, "金额")),
                List.of("id"));
    }
}
