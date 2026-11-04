package io.yak.ops.extract.master.transportor;

// todo: add event source to distinguish the event is from executor or user operation
// 将这个类移到common中?
public interface ITaskExecutionEvent {

    int getWorkflowInstanceId();

    int getTaskInstanceId();

    long getEventCreateTime();

    void setEventCreateTime(long eventCreateTime);

    Long getEventSendTime();

    void setEventSendTime(Long eventSendTime);

    void setWorkflowInstanceHost(String host);

    String getWorkflowInstanceHost();

    void setTaskInstanceHost(String host);

    String getTaskInstanceHost();

    TaskInstanceExecutionEventType getEventType();

    enum TaskInstanceExecutionEventType {
        DISPATCHED,
        RUNNING,
        PAUSED,
        KILLED,
        FAILED,
        SUCCESS,
    }

}
