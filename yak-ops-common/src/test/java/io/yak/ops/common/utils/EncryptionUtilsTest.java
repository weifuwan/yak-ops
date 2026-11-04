package io.yak.ops.common.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * encryption utils
 */
public class EncryptionUtilsTest {

    @Test
    public void testGetMd5() {
        Assertions.assertEquals(EncryptionUtils.getMd5(null), EncryptionUtils.getMd5(""));
    }

}
