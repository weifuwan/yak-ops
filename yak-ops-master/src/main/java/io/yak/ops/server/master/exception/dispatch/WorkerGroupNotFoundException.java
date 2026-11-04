package io.yak.ops.server.master.exception.dispatch;

public class WorkerGroupNotFoundException extends TaskDispatchException {

    public WorkerGroupNotFoundException(String workerGroup) {
        super("Cannot find worker group: " + workerGroup);
    }
}
