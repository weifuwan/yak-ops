package io.yak.ops.server.master.engine.task.execution;

import io.yak.ops.common.enums.Flag;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.server.master.engine.task.execution.FirstRunTaskInstanceFactory.FirstRunTaskInstanceBuilder;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Preconditions;

@Component
public class FirstRunTaskInstanceFactory extends AbstractTaskInstanceFactory<FirstRunTaskInstanceBuilder> {

    @Autowired
    private TaskInstanceDao taskInstanceDao;

    @Override
    public FirstRunTaskInstanceBuilder builder() {
        return new FirstRunTaskInstanceBuilder(this);
    }

    @Override
    public TaskInstance createTaskInstance(FirstRunTaskInstanceBuilder builder) {
        final TaskDefinition taskDefinition = Preconditions.checkNotNull(builder.taskDefinition);
        final WorkflowInstance workflowInstance = Preconditions.checkNotNull(builder.workflowInstance);

        TaskInstance taskInstance = new TaskInstance();
        injectMetadataFromTaskDefinition(taskInstance, taskDefinition);
        injectMetadataFromWorkflowInstance(taskInstance, workflowInstance);

        taskInstance.setState(TaskExecutionStatus.SUBMITTED_SUCCESS);
        taskInstance.setFirstSubmitTime(new Date());
        taskInstance.setSubmitTime(new Date());
        taskInstance.setStartTime(null);
        taskInstance.setEndTime(null);
        taskInstance.setHost(null);
        taskInstance.setExecutePath(null);
        taskInstance.setLogPath(null);
        taskInstance.setRetryTimes(0);
        taskInstance.setAlertFlag(Flag.NO);
        taskInstance.setFlag(Flag.YES);
        taskInstanceDao.insert(taskInstance);
        return taskInstance;
    }

    public static class FirstRunTaskInstanceBuilder implements ITaskInstanceFactory.ITaskInstanceBuilder {

        private final FirstRunTaskInstanceFactory firstRunTaskInstanceFactory;

        private WorkflowInstance workflowInstance;

        private TaskDefinition taskDefinition;

        public FirstRunTaskInstanceBuilder(FirstRunTaskInstanceFactory firstRunTaskInstanceFactory) {
            this.firstRunTaskInstanceFactory = firstRunTaskInstanceFactory;
        }

        public FirstRunTaskInstanceBuilder withWorkflowInstance(WorkflowInstance workflowInstance) {
            this.workflowInstance = workflowInstance;
            return this;
        }

        public FirstRunTaskInstanceBuilder withTaskDefinition(TaskDefinition taskDefinition) {
            this.taskDefinition = taskDefinition;
            return this;
        }

        @Override
        public TaskInstance build() {
            return firstRunTaskInstanceFactory.createTaskInstance(this);
        }
    }
}
