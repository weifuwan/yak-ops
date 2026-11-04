package io.yak.ops.common.thread;

/**
 * All thread used in DolphinScheduler should extend with this class to avoid the server hang issue.
 */
public abstract class BaseDaemonThread extends Thread {

    protected BaseDaemonThread(Runnable runnable) {
        super(runnable);
        this.setDaemon(true);
        this.setUncaughtExceptionHandler(DefaultUncaughtExceptionHandler.getInstance());
    }

    protected BaseDaemonThread(String threadName) {
        super();
        this.setName(threadName);
        this.setDaemon(true);
        this.setUncaughtExceptionHandler(DefaultUncaughtExceptionHandler.getInstance());
    }

}
