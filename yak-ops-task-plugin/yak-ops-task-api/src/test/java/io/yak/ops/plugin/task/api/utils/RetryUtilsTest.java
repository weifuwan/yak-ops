package io.yak.ops.plugin.task.api.utils;

import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RetryUtilsTest {

    @Test
    public void retryFunction() {
        Boolean retrySuccess = RetryUtils.retryFunction(() -> true);
        Assertions.assertTrue(retrySuccess);

        Assertions.assertThrows(RuntimeException.class, () -> {
            RetryUtils.retryFunction((Supplier<Boolean>) () -> {
                throw new RuntimeException("Test failed function");
            });
            // make sure RuntimeException thrown
            Assertions.fail();
        });

        long startTime = System.currentTimeMillis();
        Assertions.assertThrows(RuntimeException.class, () -> RetryUtils.retryFunction((Supplier<Boolean>) () -> {
            throw new RuntimeException("Test failed function");
        }, new RetryUtils.RetryPolicy(3, 1000L)));
        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;
        Assertions.assertTrue(elapsedTime >= 3000L && elapsedTime < 4000L);
    }

}
