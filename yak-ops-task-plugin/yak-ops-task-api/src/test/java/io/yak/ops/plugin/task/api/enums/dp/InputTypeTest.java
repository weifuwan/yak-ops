package io.yak.ops.plugin.task.api.enums.dp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class InputTypeTest {

    @Test
    public void testGetCode() {
        Assertions.assertEquals(0, InputType.DEFAULT.getCode());
        Assertions.assertEquals(1, InputType.STATISTICS.getCode());
        Assertions.assertEquals(2, InputType.COMPARISON.getCode());
        Assertions.assertEquals(3, InputType.CHECK.getCode());
    }

    @Test
    public void testGetDescription() {
        Assertions.assertEquals("default", InputType.DEFAULT.getDescription());
        Assertions.assertEquals("statistics", InputType.STATISTICS.getDescription());
        Assertions.assertEquals("comparison", InputType.COMPARISON.getDescription());
        Assertions.assertEquals("check", InputType.CHECK.getDescription());
    }

    @Test
    public void testOf() {
        Assertions.assertEquals(InputType.DEFAULT, InputType.of(0));
        Assertions.assertEquals(InputType.STATISTICS, InputType.of(1));
        Assertions.assertEquals(InputType.COMPARISON, InputType.of(2));
        Assertions.assertEquals(InputType.CHECK, InputType.of(3));
    }
}
