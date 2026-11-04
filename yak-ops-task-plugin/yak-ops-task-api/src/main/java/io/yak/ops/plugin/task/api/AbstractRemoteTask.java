package io.yak.ops.plugin.task.api;

import io.yak.ops.plugin.task.api.model.ApplicationInfo;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

public abstract class AbstractRemoteTask extends AbstractTask {

    /**
     * constructor
     *
     * @param taskExecutionContext taskExecutionContext
     */
    protected AbstractRemoteTask(TaskExecutionContext taskExecutionContext) {
        super(taskExecutionContext);
    }

    @Override
    public void cancel() throws TaskException {
        this.cancelApplication();
    }

    public abstract List<String> getApplicationIds() throws TaskException;

    public abstract void cancelApplication() throws TaskException;

    /**
     * If appIds is empty, submit a new remote application; otherwise, just track application status.
     *
     * @param taskCallBack
     * @throws TaskException
     */
    @Override
    public void handle(TaskCallBack taskCallBack) throws TaskException {
        // if appIds is not empty, just track application status, avoid resubmitting remote task
        if (StringUtils.isNotEmpty(taskRequest.getAppIds())) {
            setAppIds(taskRequest.getAppIds());
            trackApplicationStatus();
            return;
        }

        // submit a remote application
        submitApplication();

        if (StringUtils.isNotEmpty(getAppIds())) {
            taskRequest.setAppIds(getAppIds());
            // callback to update remote application info
            taskCallBack.updateRemoteApplicationInfo(taskRequest.getTaskInstanceId(), new ApplicationInfo(getAppIds()));
        }

        // keep tracking application status
        trackApplicationStatus();
    }

    /**
     * submit a new remote application and get application info
     *
     * @return
     * @throws TaskException
     */
    public abstract void submitApplication() throws TaskException;

    /**
     * keep checking application status
     * @throws TaskException
     */
    public abstract void trackApplicationStatus() throws TaskException;
}
