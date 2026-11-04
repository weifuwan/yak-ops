package io.yak.ops.server.master.engine.task.execution;

import io.yak.ops.dao.entity.TaskInstance;

public interface ITaskInstanceFactory<BUILDER extends ITaskInstanceFactory.ITaskInstanceBuilder> {

    BUILDER builder();

    TaskInstance createTaskInstance(BUILDER builder);

    interface ITaskInstanceBuilder {

        TaskInstance build();
    }

}
