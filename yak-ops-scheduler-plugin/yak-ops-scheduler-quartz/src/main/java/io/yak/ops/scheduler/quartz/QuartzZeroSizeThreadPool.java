package io.yak.ops.scheduler.quartz;

import org.quartz.simpl.ZeroSizeThreadPool;

public class QuartzZeroSizeThreadPool extends ZeroSizeThreadPool {

    /**
     * fix spring bug : add getter、setter method for threadCount field
     * @param count never use
     */
    public void setThreadCount(int count) {
        // do nothing
    }

    public int getThreadCount() {
        return -1;
    }
}
