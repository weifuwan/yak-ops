package io.yak.ops.plugin.task.api.utils;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

public class LogUtilsTest {

    private static final String APP_ID_FILE = LogUtilsTest.class.getResource("/appId.txt")
            .getFile();
    private static final String APP_INFO_FILE = LogUtilsTest.class.getResource("/appInfo.log")
            .getFile();

    @Test
    public void getAppIdsFromLogFile() {
        List<String> appIds = LogUtils.getAppIds(APP_ID_FILE, APP_INFO_FILE, "log");
        Assertions.assertEquals(Lists.newArrayList("application_1548381669007_1234"), appIds);
    }

    @Test
    public void getAppIdsFromAppInfoFile() {
        List<String> appIds = LogUtils.getAppIds(APP_ID_FILE, APP_INFO_FILE, "aop");
        appIds = appIds.stream().filter(a -> a.contains("application")).collect(Collectors.toList());
        Assertions.assertEquals(Lists.newArrayList("application_1548381669007_1234"), appIds);
    }
}
