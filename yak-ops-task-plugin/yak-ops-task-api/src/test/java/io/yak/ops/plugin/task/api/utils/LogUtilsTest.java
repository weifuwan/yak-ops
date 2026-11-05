package io.yak.ops.plugin.task.api.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.common.collect.Lists;

public class LogUtilsTest {

    private static final String APP_ID_FILE = LogUtilsTest.class.getResource("/appId.txt")
            .getFile();
    @TempDir
    Path temporaryDirectory;

    private String createAppInfoFile() throws IOException {
        Path appInfoFile = temporaryDirectory.resolve("appInfo.log");
        Files.write(appInfoFile,
                Collections.singletonList("application_1548381669007_1234"), StandardCharsets.UTF_8);
        return appInfoFile.toString();
    }

    @Test
    public void getAppIdsFromLogFile() {
        List<String> appIds = LogUtils.getAppIds(APP_ID_FILE, "", "log");
        Assertions.assertEquals(Lists.newArrayList("application_1548381669007_1234"), appIds);
    }

    @Test
    public void getAppIdsFromAppInfoFile() throws IOException {
        List<String> appIds = LogUtils.getAppIds(APP_ID_FILE, createAppInfoFile(), "aop");
        appIds = appIds.stream().filter(a -> a.contains("application")).collect(Collectors.toList());
        Assertions.assertEquals(Lists.newArrayList("application_1548381669007_1234"), appIds);
    }
}
