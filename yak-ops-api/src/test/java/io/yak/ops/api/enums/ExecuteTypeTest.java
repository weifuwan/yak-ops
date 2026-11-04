package io.yak.ops.api.enums;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ExecuteTypeTest {

    @Test
    public void testGetEnum() {
        Assertions.assertEquals(ExecuteType.REPEAT_RUNNING, ExecuteType.getEnum(1));
        Assertions.assertEquals(ExecuteType.RECOVER_SUSPENDED_PROCESS, ExecuteType.getEnum(2));
        Assertions.assertEquals(ExecuteType.START_FAILURE_TASK_PROCESS, ExecuteType.getEnum(3));
        Assertions.assertEquals(ExecuteType.STOP, ExecuteType.getEnum(4));
        Assertions.assertEquals(ExecuteType.PAUSE, ExecuteType.getEnum(5));
    }
}
