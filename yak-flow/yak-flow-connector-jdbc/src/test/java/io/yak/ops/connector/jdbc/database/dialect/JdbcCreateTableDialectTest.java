package io.yak.ops.connector.jdbc.database.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDdlPlan;
import io.yak.ops.core.data.TableId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JdbcCreateTableDialectTest {

    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT, false, null),
                    new Column("name", LogicalTypes.STRING, false, 100),
                    new Column("amount", LogicalTypes.decimal(18, 2), true, null)),
            List.of("id"));

    @Test
    void shouldGenerateMysqlCreateTable() {
        assertEquals(
                "CREATE TABLE `yakflow`.`orders` (`id` BIGINT NOT NULL, `name` VARCHAR(100) NOT NULL, "
                        + "`amount` DECIMAL(18,2), PRIMARY KEY (`id`))",
                JdbcDialects.forType("MYSQL")
                        .createTableSql(new TableId("yakflow", null, "orders"), SCHEMA));
    }

    @Test
    void shouldGeneratePostgresqlCreateTable() {
        assertEquals(
                "CREATE TABLE \"public\".\"orders\" (\"id\" BIGINT NOT NULL, \"name\" VARCHAR(100) NOT NULL, "
                        + "\"amount\" NUMERIC(18,2), PRIMARY KEY (\"id\"))",
                JdbcDialects.forType("POSTGRE_SQL")
                        .createTableSql(new TableId("yakflow", "public", "orders"), SCHEMA));
    }

    @Test
    void shouldGenerateOracleCreateTable() {
        assertEquals(
                "CREATE TABLE \"APP\".\"orders\" (\"id\" NUMBER(19) NOT NULL, "
                        + "\"name\" VARCHAR2(100 CHAR) NOT NULL, \"amount\" NUMBER(18,2), PRIMARY KEY (\"id\"))",
                JdbcDialects.forType("ORACLE")
                        .createTableSql(new TableId(null, "APP", "orders"), SCHEMA));
    }

    @Test
    void shouldGenerateMysqlCommentsInlineWithEscapedLiteral() {
        JdbcDdlPlan plan = JdbcDialects.forType("MYSQL")
                .createTablePlan(
                        new TableId("yakflow", null, "orders"),
                        SCHEMA,
                        "订单's table",
                        Map.of("id", "主键's id", "name", "订单名称"));

        assertEquals(1, plan.statements().size());
        assertEquals(plan.createTableSql(), plan.statements().getFirst());
        assertTrue(plan.createTableSql().contains("`id` BIGINT NOT NULL COMMENT '主键''s id'"));
        assertTrue(plan.createTableSql().contains("`name` VARCHAR(100) NOT NULL COMMENT '订单名称'"));
        assertTrue(plan.createTableSql().endsWith("COMMENT='订单''s table'"));
    }

    @Test
    void shouldGeneratePostgresqlCommentStatements() {
        JdbcDdlPlan plan = JdbcDialects.forType("POSTGRE_SQL")
                .createTablePlan(
                        new TableId("yakflow", "public", "orders"),
                        SCHEMA,
                        "订单表",
                        Map.of("id", "订单ID", "name", "订单名称"));

        assertEquals(4, plan.statements().size());
        assertEquals(plan.createTableSql(), plan.statements().getFirst());
        assertEquals("COMMENT ON TABLE \"public\".\"orders\" IS '订单表'", plan.statements().get(1));
        assertEquals(
                "COMMENT ON COLUMN \"public\".\"orders\".\"id\" IS '订单ID'",
                plan.statements().get(2));
        assertEquals(
                "COMMENT ON COLUMN \"public\".\"orders\".\"name\" IS '订单名称'",
                plan.statements().get(3));
    }

    @Test
    void shouldGenerateOracleCommentStatements() {
        JdbcDdlPlan plan = JdbcDialects.forType("ORACLE")
                .createTablePlan(
                        new TableId(null, "APP", "orders"),
                        SCHEMA,
                        "订单表",
                        Map.of("amount", "金额"));

        assertEquals(3, plan.statements().size());
        assertEquals("COMMENT ON TABLE \"APP\".\"orders\" IS '订单表'", plan.statements().get(1));
        assertEquals(
                "COMMENT ON COLUMN \"APP\".\"orders\".\"amount\" IS '金额'",
                plan.statements().get(2));
    }

    @Test
    void shouldRejectMysqlTimestampWithTimeZone() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> JdbcDialects.forType("MYSQL")
                        .nativeType(new Column(
                                "event_time", LogicalTypes.TIMESTAMP_WITH_TIME_ZONE, true, null)));
    }

    @Test
    void shouldRejectOracleTime() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> JdbcDialects.forType("ORACLE")
                        .nativeType(new Column("event_time", LogicalTypes.TIME, true, null)));
    }

    @Test
    void shouldMarkOracleClobAsUnsupportedPrimaryKeyType() {
        JdbcNativeType type =
                JdbcDialects.forType("ORACLE").nativeType(new Column("payload", LogicalTypes.STRING, false, null));

        assertEquals("CLOB", type.ddl());
        assertFalse(type.primaryKeySupported());
    }
}
