package io.yak.ops.server.master.engine.task.execution;

import io.yak.ops.common.enums.Flag;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.server.master.engine.task.execution.RetryTaskInstanceFactory.RetryTaskInstanceBuilder;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RetryTaskInstanceFactory extends AbstractTaskInstanceFactory<RetryTaskInstanceBuilder> {

    @Autowired
    private TaskInstanceDao taskInstanceDao;

    @Override
    public RetryTaskInstanceBuilder builder() {
        return new RetryTaskInstanceBuilder(this);
    }

    @Transactional
    @Override
    public TaskInstance createTaskInstance(RetryTaskInstanceBuilder builder) {
        final TaskInstance needRetryTaskInstance = builder.taskInstance;
        final TaskInstance taskInstance = cloneTaskInstance(needRetryTaskInstance);
        taskInstance.setId(null);
        taskInstance.setState(TaskExecutionStatus.SUBMITTED_SUCCESS);
        taskInstance.setPid(0);
        taskInstance.setHost(null);
        taskInstance.setExecutePath(null);
        taskInstance.setLogPath(null);
        taskInstance.setStartTime(null);
        taskInstance.setEndTime(null);
        taskInstance.setSubmitTime(new Date());
        taskInstance.setRetryTimes(taskInstance.getRetryTimes() + 1);
        taskInstanceDao.insert(taskInstance);

        needRetryTaskInstance.setFlag(Flag.NO);
        taskInstanceDao.updateById(needRetryTaskInstance);
        return taskInstance;
    }

    public static class RetryTaskInstanceBuilder implements ITaskInstanceFactory.ITaskInstanceBuilder {

        private final RetryTaskInstanceFactory retryTaskInstanceFactory;

        private TaskInstance taskInstance;

        public RetryTaskInstanceBuilder(RetryTaskInstanceFactory retryTaskInstanceFactory) {
            this.retryTaskInstanceFactory = retryTaskInstanceFactory;
        }

        public RetryTaskInstanceBuilder withTaskInstance(TaskInstance taskInstance) {
            this.taskInstance = taskInstance;
            return this;
        }

        @Override
        public TaskInstance build() {
            return retryTaskInstanceFactory.createTaskInstance(this);
        }

    }
}
