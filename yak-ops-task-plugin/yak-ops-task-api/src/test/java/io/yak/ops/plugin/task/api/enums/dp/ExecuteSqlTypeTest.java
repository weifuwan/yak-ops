package io.yak.ops.plugin.task.api.enums.dp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ExecuteSqlTypeTest {

    @Test
    public void testGetCode() {
        Assertions.assertEquals(0, ExecuteSqlType.MIDDLE.getCode());
        Assertions.assertEquals(1, ExecuteSqlType.STATISTICS.getCode());
        Assertions.assertEquals(2, ExecuteSqlType.COMPARISON.getCode());
    }

    @Test
    public void testGetDescription() {
        Assertions.assertEquals("middle", ExecuteSqlType.MIDDLE.getDescription());
        Assertions.assertEquals("statistics", ExecuteSqlType.STATISTICS.getDescription());
        Assertions.assertEquals("comparison", ExecuteSqlType.COMPARISON.getDescription());
    }

    @Test
    public void testOf() {
        Assertions.assertEquals(ExecuteSqlType.MIDDLE, ExecuteSqlType.of(0));
        Assertions.assertEquals(ExecuteSqlType.STATISTICS, ExecuteSqlType.of(1));
        Assertions.assertEquals(ExecuteSqlType.COMPARISON, ExecuteSqlType.of(2));
    }
}
