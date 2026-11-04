package io.yak.ops.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MaskUtilsTest {

    @Test
    void testMaskString() {
        assertEquals("****", MaskUtils.maskString("sensitiveData"));
        assertEquals("****", MaskUtils.maskString(""));
        assertEquals(null, MaskUtils.maskString(null));
    }

    @Test
    void testMaskStringWithStartIndex() {
        assertEquals("****", MaskUtils.maskString("sensitiveData", 0));
        assertEquals("se****", MaskUtils.maskString("sensitiveData", 2));
        assertEquals("****", MaskUtils.maskString("", 0));
        assertEquals(null, MaskUtils.maskString(null, 1));
    }
}
