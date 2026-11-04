package io.yak.ops.plugin.task.api.enums.dp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConnectorTypeTest {

    @Test
    public void testGetCode() {
        Assertions.assertEquals(0, ConnectorType.JDBC.getCode());
        Assertions.assertEquals(1, ConnectorType.HIVE.getCode());
    }

    @Test
    public void testGetDescription() {
        Assertions.assertEquals("JDBC", ConnectorType.JDBC.getDescription());
        Assertions.assertEquals("HIVE", ConnectorType.HIVE.getDescription());
    }

    @Test
    public void testOf() {
        Assertions.assertEquals(ConnectorType.JDBC, ConnectorType.of(0));
    }
}
