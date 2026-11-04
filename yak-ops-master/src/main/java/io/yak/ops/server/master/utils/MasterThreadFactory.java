package io.yak.ops.server.master.utils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class MasterThreadFactory {

    public static ScheduledExecutorService getDefaultSchedulerThreadExecutor() {
        return Executors.newSingleThreadScheduledExecutor();
    }

}
