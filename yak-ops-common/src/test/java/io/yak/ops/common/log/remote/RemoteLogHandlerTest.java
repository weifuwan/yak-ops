package io.yak.ops.common.log.remote;

import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.utils.LogUtils;
import io.yak.ops.common.utils.PropertyUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class RemoteLogHandlerTest {

    @Test
    public void testGetObjectNameFromLogPath() {
        Path currentRelativePath = Paths.get("");
        String currentDir = currentRelativePath.toAbsolutePath().toString();
        final String logPath = currentDir + "/logs/20230116/8245922982496/1/1/1.log";
        log.info("logPath is: {}", logPath);

        final String expectedObjectName = "logs/20230116/8245922982496/1/1/1.log";

        try (
                MockedStatic<PropertyUtils> propertyUtilsMockedStatic = Mockito.mockStatic(PropertyUtils.class);
                MockedStatic<LogUtils> remoteLogUtilsMockedStatic = Mockito.mockStatic(LogUtils.class)) {
            propertyUtilsMockedStatic.when(() -> PropertyUtils.getString(Constants.REMOTE_LOGGING_BASE_DIR))
                    .thenReturn("logs");
            remoteLogUtilsMockedStatic.when(LogUtils::getLocalLogBaseDir).thenReturn("logs");

            String objectName = RemoteLogUtils.getObjectNameFromLogPath(logPath);
            Assertions.assertEquals(expectedObjectName, objectName);
        }
    }
}
