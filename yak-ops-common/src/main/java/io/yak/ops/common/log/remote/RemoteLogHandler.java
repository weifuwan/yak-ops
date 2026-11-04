package io.yak.ops.common.log.remote;

public interface RemoteLogHandler {

    void sendRemoteLog(String logPath);

    void getRemoteLog(String logPath);
}
