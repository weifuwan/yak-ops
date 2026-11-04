package io.yak.ops.common.exception;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ExceptionTest {

    @Test
    public void testException() {
        final String message = "Test";
        RuntimeException time = new RuntimeException(message);

        Assertions.assertNull(new BaseException().getMessage());
        Assertions.assertNotNull(new BaseException(message).getMessage());
        Assertions.assertNotNull(new BaseException(message, time).getMessage());
        Assertions.assertNotNull(new BaseException(time).getCause());
        Assertions.assertNotNull(new BaseException(message, time, false, false).getMessage());

        Assertions.assertNull(new StorageOperateNoConfiguredException().getMessage());
        Assertions.assertNotNull(new StorageOperateNoConfiguredException(message).getMessage());
        Assertions.assertNotNull(new StorageOperateNoConfiguredException(message, time).getMessage());
        Assertions.assertNotNull(new StorageOperateNoConfiguredException(time).getCause());
        Assertions.assertNotNull(new StorageOperateNoConfiguredException(message, time, false, false).getMessage());
    }
}
