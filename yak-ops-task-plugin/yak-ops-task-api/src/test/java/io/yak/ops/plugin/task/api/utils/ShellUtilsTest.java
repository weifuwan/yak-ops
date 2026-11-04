package io.yak.ops.plugin.task.api.utils;

import java.util.ArrayList;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ShellUtilsTest {

    @Test
    public void testGetEnvSourceList() {
        Assertions.assertEquals(new ArrayList<>(), ShellUtils.ENV_SOURCE_LIST);
    }

}
