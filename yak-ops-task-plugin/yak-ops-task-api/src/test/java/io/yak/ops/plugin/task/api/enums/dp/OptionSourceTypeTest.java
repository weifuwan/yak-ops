package io.yak.ops.plugin.task.api.enums.dp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OptionSourceTypeTest {

    @Test
    public void testGetCode() {
        Assertions.assertEquals(0, OptionSourceType.DEFAULT.getCode());
        Assertions.assertEquals(1, OptionSourceType.DATASOURCE_ID.getCode());
        Assertions.assertEquals(2, OptionSourceType.DATASOURCE_TYPE.getCode());
        Assertions.assertEquals(3, OptionSourceType.COMPARISON_TYPE.getCode());
    }

    @Test
    public void testGetDescription() {
        Assertions.assertEquals("default", OptionSourceType.DEFAULT.getDescription());
        Assertions.assertEquals("datasource_id", OptionSourceType.DATASOURCE_ID.getDescription());
        Assertions.assertEquals("datasource_type", OptionSourceType.DATASOURCE_TYPE.getDescription());
        Assertions.assertEquals("comparison_type", OptionSourceType.COMPARISON_TYPE.getDescription());
    }

    @Test
    public void testOf() {
        Assertions.assertEquals(OptionSourceType.DEFAULT, OptionSourceType.of(0));
        Assertions.assertEquals(OptionSourceType.DATASOURCE_ID, OptionSourceType.of(1));
        Assertions.assertEquals(OptionSourceType.DATASOURCE_TYPE, OptionSourceType.of(2));
        Assertions.assertEquals(OptionSourceType.COMPARISON_TYPE, OptionSourceType.of(3));
    }
}
