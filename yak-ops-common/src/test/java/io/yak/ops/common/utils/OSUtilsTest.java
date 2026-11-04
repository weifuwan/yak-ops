package io.yak.ops.common.utils;

import org.apache.commons.lang3.SystemUtils;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OSUtilsTest {

    @Test
    public void existTenantCodeInLinux() {
        if (SystemUtils.IS_OS_LINUX) {
            boolean test = OSUtils.existTenantCodeInLinux("root");
            Assertions.assertTrue(test);
            boolean test1 = OSUtils.existTenantCodeInLinux("xxxtt");
            Assertions.assertFalse(test1);
        } else {
            Assertions.assertFalse(false, "system must be linux");
        }

    }

    @Test
    public void existOSTenandCode() {
        if (SystemUtils.IS_OS_LINUX) {
            List<String> userList = OSUtils.getUserList();
            Assertions.assertTrue(userList.contains("root"));
            Assertions.assertFalse(userList.contains("xxxtt"));
        } else {
            Assertions.assertFalse(false, "system must be linux");
        }
    }

    @Test
    void getTotalSystemMemory() throws InterruptedException {
        double totalSystemMemory = OSUtils.getTotalSystemMemory();
        Assertions.assertTrue(totalSystemMemory > 0);
        // Assert that the memory is not changed
        Thread.sleep(1000L);
        Assertions.assertEquals(totalSystemMemory, OSUtils.getTotalSystemMemory());
    }

    @Test
    void getSystemMemoryAvailable() {
        long systemAvailableMemoryUsed = OSUtils.getSystemAvailableMemoryUsed();
        Assertions.assertTrue(systemAvailableMemoryUsed > 0);
    }

    @Test
    void getSystemMemoryUsedPercentage() {
        long totalSystemMemory = OSUtils.getTotalSystemMemory();
        long systemMemoryAvailable = OSUtils.getSystemAvailableMemoryUsed();
        double systemAvailableMemoryUsedPercentage =
                (double) (totalSystemMemory - systemMemoryAvailable) / totalSystemMemory;

        Assertions.assertTrue(systemAvailableMemoryUsedPercentage > 0);
    }
}
