package io.yak.ops.plugin.task.api;

import io.yak.ops.plugin.task.api.model.ApplicationInfo;

public interface TaskCallBack {

    void updateRemoteApplicationInfo(int taskInstanceId, ApplicationInfo applicationInfo);

    // todo:The pid should put into runtime context
    @Deprecated
    void updateTaskInstanceInfo(int taskInstanceId);
}
