package io.yak.ops.server.master.engine.task.execution;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskInstanceFactories {

    @Autowired
    private FirstRunTaskInstanceFactory firstRunTaskInstanceFactory;

    @Autowired
    private RetryTaskInstanceFactory retryTaskInstanceFactory;

    @Autowired
    private FailedRecoverTaskInstanceFactory failedRecoverTaskInstanceFactory;

    @Autowired
    private PauseRecoverTaskInstanceFactory pauseRecoverTaskInstanceFactory;

    @Autowired
    private FailoverTaskInstanceFactory failoverTaskInstanceFactory;

    public FirstRunTaskInstanceFactory firstRunTaskInstanceFactory() {
        return firstRunTaskInstanceFactory;
    }

    public RetryTaskInstanceFactory retryTaskInstanceFactory() {
        return retryTaskInstanceFactory;
    }

    public FailedRecoverTaskInstanceFactory failedRecoverTaskInstanceFactory() {
        return failedRecoverTaskInstanceFactory;
    }

    public FailoverTaskInstanceFactory failoverTaskInstanceFactory() {
        return failoverTaskInstanceFactory;
    }

    public PauseRecoverTaskInstanceFactory pauseRecoverTaskInstanceFactory() {
        return pauseRecoverTaskInstanceFactory;
    }
}
