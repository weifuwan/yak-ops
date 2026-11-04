package io.yak.ops.common.log.remote;

import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.utils.PropertyUtils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class RemoteLogHandlerFactory {

    public RemoteLogHandler getRemoteLogHandler() {
        if (!RemoteLogUtils.isRemoteLoggingEnable()) {
            return null;
        }

        String target = PropertyUtils.getUpperCaseString(Constants.REMOTE_LOGGING_TARGET);
        if ("OSS".equals(target)) {
            return OssRemoteLogHandler.getInstance();
        } else if ("S3".equals(target)) {
            return S3RemoteLogHandler.getInstance();
        }

        log.error("No suitable remote logging target for {}", target);
        return null;
    }
}
