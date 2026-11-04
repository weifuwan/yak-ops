package io.yak.ops.common.log.remote;

import static io.yak.ops.common.utils.LogUtils.getLocalLogBaseDir;

import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.utils.PropertyUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RemoteLogUtils {

    private static RemoteLogService remoteLogService;

    @Autowired
    private RemoteLogService autowiredRemoteLogService;

    @PostConstruct
    private void init() {
        remoteLogService = autowiredRemoteLogService;
    }

    public static void sendRemoteLog(String logPath) {
        if (isRemoteLoggingEnable()) {
            // send task logs to remote storage asynchronously
            remoteLogService.asyncSendRemoteLog(logPath);
        }
    }

    public static void getRemoteLog(String logPath) {
        if (isRemoteLoggingEnable()) {
            log.info("Start to get log {} from remote target {}", logPath,
                    PropertyUtils.getString(Constants.REMOTE_LOGGING_TARGET));

            mkdirOfLog(logPath);
            RemoteLogHandler remoteLogHandler = RemoteLogHandlerFactory.getRemoteLogHandler();
            if (remoteLogHandler == null) {
                return;
            }
            remoteLogHandler.getRemoteLog(logPath);
            log.info("End get log {} from remote target {}", logPath,
                    PropertyUtils.getString(Constants.REMOTE_LOGGING_TARGET));
        }
    }

    private static void mkdirOfLog(String logPath) {
        Path directory = Paths.get(logPath).getParent();
        directory.toFile().mkdirs();
    }

    public static boolean isRemoteLoggingEnable() {
        return PropertyUtils.getBoolean(Constants.REMOTE_LOGGING_ENABLE, Boolean.FALSE);
    }

    public static String getObjectNameFromLogPath(String logPath) {
        Path localLogBaseDirPath = Paths.get(getLocalLogBaseDir()).toAbsolutePath();

        Path path = Paths.get(logPath);
        int nameCount = path.getNameCount();

        String remoteLogBaseDir = PropertyUtils.getString(Constants.REMOTE_LOGGING_BASE_DIR);
        return Paths.get(remoteLogBaseDir, path.subpath(localLogBaseDirPath.getNameCount(), nameCount).toString())
                .toString();
    }
}
