package io.yak.ops.modeling.ddl.doris;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DorisDdlFormatterTest {

    private final DorisDdlFormatter formatter = new DorisDdlFormatter();

    @Test
    void formatsCreateTableColumnsAndClausesWithoutSplittingQuotedCommasOrNestedTypes() {
        String raw = "CREATE TABLE `warehouse`.`orders`(`id` BIGINT NOT NULL COMMENT 'id',"
                + "`amount` DECIMALV3(18,2) NULL COMMENT 'amount, (gross)',"
                + "`note` STRING NULL COMMENT 'it''s, okay') UNIQUE KEY(`id`) "
                + "COMMENT 'orders, current' DISTRIBUTED BY HASH(`id`) BUCKETS AUTO";

        String expected = "CREATE TABLE `warehouse`.`orders` (\n"
                + "    `id` BIGINT NOT NULL COMMENT 'id',\n"
                + "    `amount` DECIMALV3(18,2) NULL COMMENT 'amount, (gross)',\n"
                + "    `note` STRING NULL COMMENT 'it''s, okay'\n"
                + ")\n"
                + "UNIQUE KEY(`id`)\n"
                + "COMMENT 'orders, current'\n"
                + "DISTRIBUTED BY HASH(`id`) BUCKETS AUTO";

        String formatted = formatter.format(raw);

        assertEquals(expected, formatted);
        assertEquals(formatted, formatter.format(formatted));
    }

    @Test
    void formatsIncrementalAlterStatementsWithAnIndentedOperation() {
        String raw = "ALTER TABLE `warehouse`.`orders` ADD COLUMN `note` VARCHAR(60) NULL "
                + "COMMENT 'a, b'";

        assertEquals(
                "ALTER TABLE `warehouse`.`orders`\n"
                        + "    ADD COLUMN `note` VARCHAR(60) NULL COMMENT 'a, b'",
                formatter.format(raw));
    }

    @Test
    void leavesNoChangeCommentsAndUnsupportedStatementsUntouched() {
        String noChanges = "-- 当前目标表结构与模型一致，无需执行变更。";
        String drop = "DROP TABLE IF EXISTS `warehouse`.`orders`";

        assertEquals(noChanges, formatter.format(noChanges));
        assertEquals(drop, formatter.format(drop));
        assertEquals("", formatter.format("  "));
        assertEquals(null, formatter.format(null));
    }
}
