package io.yak.ops.common.lifecycle;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class ServerLifeCycleManager {

    private static volatile ServerStatus serverStatus = ServerStatus.RUNNING;

    private static long serverStartupTime = System.currentTimeMillis();

    public static long getServerStartupTime() {
        return serverStartupTime;
    }

    public static void toRunning() {
        serverStatus = ServerStatus.RUNNING;
    }

    public static boolean isStopped() {
        return serverStatus == ServerStatus.STOPPED;
    }

    public static ServerStatus getServerStatus() {
        return serverStatus;
    }

    public static synchronized boolean toStopped() {
        if (serverStatus == ServerStatus.STOPPED) {
            return false;
        }
        log.info("The current server status changed from {} to {}", serverStatus, ServerStatus.STOPPED);
        serverStatus = ServerStatus.STOPPED;
        return true;
    }

}
