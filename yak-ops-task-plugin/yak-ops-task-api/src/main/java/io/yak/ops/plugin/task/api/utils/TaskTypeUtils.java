package io.yak.ops.plugin.task.api.utils;

import static com.google.common.base.Preconditions.checkArgument;

import io.yak.ops.plugin.task.api.ILogicTaskChannel;
import io.yak.ops.plugin.task.api.TaskPluginManager;
import io.yak.ops.plugin.task.api.task.ConditionsLogicTaskChannelFactory;
import io.yak.ops.plugin.task.api.task.DependentLogicTaskChannelFactory;
import io.yak.ops.plugin.task.api.task.SubWorkflowLogicTaskChannelFactory;
import io.yak.ops.plugin.task.api.task.SwitchLogicTaskChannelFactory;

import org.apache.commons.lang3.StringUtils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TaskTypeUtils {

    public boolean isSwitchTask(String taskType) {
        return SwitchLogicTaskChannelFactory.NAME.equals(taskType);
    }

    public boolean isConditionTask(String taskType) {
        return ConditionsLogicTaskChannelFactory.NAME.equals(taskType);
    }

    public boolean isSubWorkflowTask(String taskType) {
        return SubWorkflowLogicTaskChannelFactory.NAME.equals(taskType);
    }

    public boolean isDependentTask(String taskType) {
        return DependentLogicTaskChannelFactory.NAME.equals(taskType);
    }

    public boolean isLogicTask(String taskType) {
        checkArgument(StringUtils.isNotEmpty(taskType), "taskType cannot be empty");
        return TaskPluginManager.getTaskChannel(taskType) instanceof ILogicTaskChannel;
    }

}
