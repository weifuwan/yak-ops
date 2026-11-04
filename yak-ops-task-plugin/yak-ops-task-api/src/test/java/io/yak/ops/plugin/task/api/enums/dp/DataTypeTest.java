package io.yak.ops.plugin.task.api.enums.dp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DataTypeTest {

    /**
     * 0-string
     * 1-list
     * 2-number
     * 3-sql
     */
    @Test
    public void testGetCode() {
        Assertions.assertEquals(0, DataType.STRING.getCode());
        Assertions.assertEquals(1, DataType.LIST.getCode());
        Assertions.assertEquals(2, DataType.NUMBER.getCode());
        Assertions.assertEquals(3, DataType.LIKE_SQL.getCode());
    }

    @Test
    public void testGetDescription() {
        Assertions.assertEquals("string", DataType.STRING.getDescription());
        Assertions.assertEquals("list", DataType.LIST.getDescription());
        Assertions.assertEquals("number", DataType.NUMBER.getDescription());
        Assertions.assertEquals("sql", DataType.LIKE_SQL.getDescription());
    }

    @Test
    public void testOf() {
        Assertions.assertEquals(DataType.STRING, DataType.of(0));
        Assertions.assertEquals(DataType.LIST, DataType.of(1));
        Assertions.assertEquals(DataType.NUMBER, DataType.of(2));
        Assertions.assertEquals(DataType.LIKE_SQL, DataType.of(3));
    }
}
