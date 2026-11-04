package io.yak.ops.plugin.task.api.enums.dp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OperatorTypeTest {

    @Test
    public void testGetCode() {
        Assertions.assertEquals(0, OperatorType.EQ.getCode());
        Assertions.assertEquals(1, OperatorType.LT.getCode());
        Assertions.assertEquals(2, OperatorType.LE.getCode());
        Assertions.assertEquals(3, OperatorType.GT.getCode());
        Assertions.assertEquals(4, OperatorType.GE.getCode());
        Assertions.assertEquals(5, OperatorType.NE.getCode());
    }

    @Test
    public void testGetDescription() {
        Assertions.assertEquals("equal", OperatorType.EQ.getDescription());
        Assertions.assertEquals("little than", OperatorType.LT.getDescription());
        Assertions.assertEquals("little and equal", OperatorType.LE.getDescription());
        Assertions.assertEquals("great than", OperatorType.GT.getDescription());
        Assertions.assertEquals("great and equal", OperatorType.GE.getDescription());
        Assertions.assertEquals("not equal", OperatorType.NE.getDescription());
    }

    @Test
    public void testOf() {
        Assertions.assertEquals(OperatorType.EQ, OperatorType.of(0));
        Assertions.assertEquals(OperatorType.LT, OperatorType.of(1));
        Assertions.assertEquals(OperatorType.LE, OperatorType.of(2));
        Assertions.assertEquals(OperatorType.GT, OperatorType.of(3));
        Assertions.assertEquals(OperatorType.GE, OperatorType.of(4));
        Assertions.assertEquals(OperatorType.NE, OperatorType.of(5));
    }
}
