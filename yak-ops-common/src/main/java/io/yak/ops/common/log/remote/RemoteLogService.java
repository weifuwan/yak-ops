package io.yak.ops.common.log.remote;

import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.utils.PropertyUtils;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RemoteLogService {

    @Async("remoteLogHandleExecutor")
    public void asyncSendRemoteLog(String logPath) {
        if (RemoteLogUtils.isRemoteLoggingEnable()) {
            log.info("Start to send log {} to remote target {}", logPath,
                    PropertyUtils.getString(Constants.REMOTE_LOGGING_TARGET));

            RemoteLogHandler remoteLogHandler = RemoteLogHandlerFactory.getRemoteLogHandler();
            if (remoteLogHandler == null) {
                return;
            }
            remoteLogHandler.sendRemoteLog(logPath);
            log.info("End send log {} to remote target {}", logPath,
                    PropertyUtils.getString(Constants.REMOTE_LOGGING_TARGET));
        }
    }
}
