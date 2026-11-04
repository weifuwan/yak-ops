package io.yak.ops.plugin.task.api.enums.dp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CheckTypeTest {

    @Test
    public void testGetCode() {
        Assertions.assertEquals(0, CheckType.COMPARISON_MINUS_STATISTICS.getCode());
        Assertions.assertEquals(1, CheckType.STATISTICS_MINUS_COMPARISON.getCode());
        Assertions.assertEquals(2, CheckType.STATISTICS_COMPARISON_PERCENTAGE.getCode());
        Assertions.assertEquals(3, CheckType.STATISTICS_COMPARISON_DIFFERENCE_COMPARISON_PERCENTAGE.getCode());
    }

    @Test
    public void testGetDescription() {
        Assertions.assertEquals("comparison_minus_statistics", CheckType.COMPARISON_MINUS_STATISTICS.getDescription());
        Assertions.assertEquals("statistics_minus_comparison", CheckType.STATISTICS_MINUS_COMPARISON.getDescription());
        Assertions.assertEquals("statistics_comparison_percentage",
                CheckType.STATISTICS_COMPARISON_PERCENTAGE.getDescription());
        Assertions.assertEquals("statistics_comparison_difference_comparison_percentage",
                CheckType.STATISTICS_COMPARISON_DIFFERENCE_COMPARISON_PERCENTAGE.getDescription());
    }

    @Test
    public void testOf() {
        Assertions.assertEquals(CheckType.COMPARISON_MINUS_STATISTICS, CheckType.of(0));
    }
}
