package io.yak.ops.plugin.task.api.parameters;

import io.yak.ops.plugin.task.api.enums.TaskTimeoutStrategy;

public class TaskTimeoutParameter {

    private boolean enable;
    /**
     * task timeout strategy
     */
    private TaskTimeoutStrategy strategy;
    /**
     * task timeout interval
     */
    private int interval;

    public boolean getEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public TaskTimeoutStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(TaskTimeoutStrategy strategy) {
        this.strategy = strategy;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public TaskTimeoutParameter() {
    }

    public TaskTimeoutParameter(boolean enable) {
        this.enable = enable;
    }

    public TaskTimeoutParameter(boolean enable, TaskTimeoutStrategy strategy, int interval) {
        this.enable = enable;
        this.strategy = strategy;
        this.interval = interval;
    }

    @Override
    public String toString() {
        return "TaskTimeoutParameter{" +
                "enable=" + enable +
                ", strategy=" + strategy +
                ", interval=" + interval +
                '}';
    }
}
