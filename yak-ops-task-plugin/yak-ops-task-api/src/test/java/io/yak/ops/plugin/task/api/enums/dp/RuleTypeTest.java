package io.yak.ops.plugin.task.api.enums.dp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RuleTypeTest {

    @Test
    public void testGetCode() {
        Assertions.assertEquals(0, RuleType.SINGLE_TABLE.getCode());
        Assertions.assertEquals(1, RuleType.SINGLE_TABLE_CUSTOM_SQL.getCode());
        Assertions.assertEquals(2, RuleType.MULTI_TABLE_ACCURACY.getCode());
        Assertions.assertEquals(3, RuleType.MULTI_TABLE_COMPARISON.getCode());
    }

    @Test
    public void testGetDescription() {
        Assertions.assertEquals("single_table", RuleType.SINGLE_TABLE.getDescription());
        Assertions.assertEquals("single_table_custom_sql", RuleType.SINGLE_TABLE_CUSTOM_SQL.getDescription());
        Assertions.assertEquals("multi_table_accuracy", RuleType.MULTI_TABLE_ACCURACY.getDescription());
        Assertions.assertEquals("multi_table_comparison", RuleType.MULTI_TABLE_COMPARISON.getDescription());
    }

    @Test
    public void testOf() {
        Assertions.assertEquals(RuleType.SINGLE_TABLE, RuleType.of(0));
        Assertions.assertEquals(RuleType.SINGLE_TABLE_CUSTOM_SQL, RuleType.of(1));
        Assertions.assertEquals(RuleType.MULTI_TABLE_ACCURACY, RuleType.of(2));
        Assertions.assertEquals(RuleType.MULTI_TABLE_COMPARISON, RuleType.of(3));
    }
}
