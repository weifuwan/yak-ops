package io.yak.ops.server.master.exception.dispatch;

public class NoAvailableWorkerException extends TaskDispatchException {

    public NoAvailableWorkerException(String workerGroup) {
        super("Cannot find available worker under worker group: " + workerGroup);
    }
}
