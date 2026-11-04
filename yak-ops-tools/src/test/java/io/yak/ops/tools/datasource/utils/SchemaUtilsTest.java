package io.yak.ops.tools.datasource.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SchemaUtilsTest {

    @Test
    void testDatabaseBaseline() throws Exception {
        Assertions.assertEquals("3.4.3", SchemaUtils.getSoftVersion());
    }
}
