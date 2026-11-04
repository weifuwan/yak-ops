package io.yak.ops.server.master.engine.task.execution;

import io.yak.ops.common.enums.Flag;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.server.master.engine.ITaskGroupCoordinator;
import io.yak.ops.server.master.engine.task.execution.FailoverTaskInstanceFactory.FailoverTaskInstanceBuilder;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FailoverTaskInstanceFactory extends AbstractTaskInstanceFactory<FailoverTaskInstanceBuilder> {

    @Autowired
    private TaskInstanceDao taskInstanceDao;

    @Autowired
    private ITaskGroupCoordinator taskGroupCoordinator;

    @Override
    public FailoverTaskInstanceFactory.FailoverTaskInstanceBuilder builder() {
        return new FailoverTaskInstanceBuilder(this);
    }

    @Transactional
    @Override
    public TaskInstance createTaskInstance(FailoverTaskInstanceBuilder builder) {
        final TaskInstance needFailoverTaskInstance = builder.needFailoverTaskInstance;
        final TaskInstance taskInstance = cloneTaskInstance(needFailoverTaskInstance);
        taskInstance.setId(null);
        taskInstance.setState(TaskExecutionStatus.SUBMITTED_SUCCESS);
        taskInstance.setHost(null);
        taskInstance.setVarPool(null);
        taskInstance.setSubmitTime(new Date());
        taskInstance.setLogPath(null);
        taskInstance.setExecutePath(null);
        taskInstanceDao.insert(taskInstance);

        if (taskGroupCoordinator.needToReleaseTaskGroupSlot(needFailoverTaskInstance)) {
            taskGroupCoordinator.releaseTaskGroupSlot(needFailoverTaskInstance);
        }

        needFailoverTaskInstance.setFlag(Flag.NO);
        needFailoverTaskInstance.setState(TaskExecutionStatus.NEED_FAULT_TOLERANCE);
        taskInstanceDao.updateById(needFailoverTaskInstance);
        return taskInstance;
    }

    public static class FailoverTaskInstanceBuilder implements ITaskInstanceFactory.ITaskInstanceBuilder {

        private final FailoverTaskInstanceFactory failoverTaskInstanceFactory;

        private TaskInstance needFailoverTaskInstance;

        public FailoverTaskInstanceBuilder(FailoverTaskInstanceFactory failoverTaskInstanceFactory) {
            this.failoverTaskInstanceFactory = failoverTaskInstanceFactory;
        }

        public FailoverTaskInstanceBuilder withTaskInstance(TaskInstance needFailoverTaskInstance) {
            this.needFailoverTaskInstance = needFailoverTaskInstance;
            return this;
        }

        @Override
        public TaskInstance build() {
            return failoverTaskInstanceFactory.createTaskInstance(this);
        }
    }
}
